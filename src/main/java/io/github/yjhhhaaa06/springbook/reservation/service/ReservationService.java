package io.github.yjhhhaaa06.springbook.reservation.service;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.config.BookProperties;
import io.github.yjhhhaaa06.springbook.reader.mapper.ReaderMapper;
import io.github.yjhhhaaa06.springbook.reservation.domain.Reservation;
import io.github.yjhhhaaa06.springbook.reservation.dto.ReservationResponse;
import io.github.yjhhhaaa06.springbook.reservation.mapper.ReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 预约业务（FIFO 队首优先）。
 *
 * <p>状态机：WAITING → READY → FULFILLED，旁路 CANCELLED / EXPIRED。
 * 归还时若队列非空，队首 WAITING 置 READY 并占用一个可借名额（hold_until = now + 48h）；
 * READY 超期未取则置 EXPIRED 并顺延下一位（惰性清理，在借书、还书、查预约时触发，不引入定时任务）。
 *
 * <p>库存口径：READY 预约会占用一个 available_copies（借出履约时不再扣减）；
 * READY 失效（EXPIRED/CANCELLED）时该名额顺延给下一位 WAITING，若队列已空则归还库存。
 * 涉及库存/队列流转的方法都要求调用方先对 books 行加锁（lockById），保证同书操作串行。
 */
@Service
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final BookMapper bookMapper;
    private final ReaderMapper readerMapper;
    private final BookProperties props;

    public ReservationService(ReservationMapper reservationMapper, BookMapper bookMapper,
                              ReaderMapper readerMapper, BookProperties props) {
        this.reservationMapper = reservationMapper;
        this.bookMapper = bookMapper;
        this.readerMapper = readerMapper;
        this.props = props;
    }

    /**
     * 发起预约：仅当可借数量为 0 时允许，同一读者对同一本书只能有一条活跃预约。
     */
    @Transactional
    public ReservationResponse create(Long bookId, Long readerId) {
        Book book = bookMapper.findById(bookId);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        if (readerMapper.findById(readerId) == null) {
            throw new BizException(ErrorCode.READER_NOT_FOUND);
        }
        if (book.getAvailableCopies() > 0) {
            throw new BizException(ErrorCode.RESERVATION_NOT_ALLOWED);
        }
        if (reservationMapper.countActiveByReaderAndBook(bookId, readerId) > 0) {
            throw new BizException(ErrorCode.DUPLICATE_ACTIVE_RESERVATION);
        }
        Reservation reservation = new Reservation();
        reservation.setBookId(bookId);
        reservation.setReaderId(readerId);
        reservation.setStatus(Reservation.STATUS_WAITING);
        reservationMapper.insert(reservation);
        return ReservationResponse.from(reservationMapper.findById(reservation.getId()));
    }

    /**
     * 取消预约：WAITING 直接取消；READY 取消后须释放占用名额（顺延下一位或归还库存）。
     */
    @Transactional
    public ReservationResponse cancel(Long reservationId) {
        Reservation reservation = reservationMapper.findById(reservationId);
        if (reservation == null) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        String status = reservation.getStatus();
        if (!Reservation.STATUS_WAITING.equals(status) && !Reservation.STATUS_READY.equals(status)) {
            throw new BizException(ErrorCode.RESERVATION_NOT_CANCELLABLE);
        }
        if (Reservation.STATUS_READY.equals(status)) {
            bookMapper.lockById(reservation.getBookId());
            reservationMapper.updateStatus(reservationId, Reservation.STATUS_CANCELLED, null, null);
            handOverReservation(reservation.getBookId(), LocalDateTime.now());
        } else {
            reservationMapper.updateStatus(reservationId, Reservation.STATUS_CANCELLED, null, null);
        }
        return ReservationResponse.from(reservationMapper.findById(reservationId));
    }

    /**
     * 查看某书的预约队列（查询时触发惰性清理）。
     */
    @Transactional
    public List<ReservationResponse> listByBook(Long bookId) {
        if (bookMapper.findById(bookId) == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        bookMapper.lockById(bookId);
        expireStaleReady(bookId, LocalDateTime.now());
        return reservationMapper.listByBook(bookId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    /**
     * 惰性清理：把超过保留期的 READY 置 EXPIRED，并逐个顺延。
     * 调用方必须已在事务内对 books 行加锁。
     */
    public void expireStaleReady(Long bookId, LocalDateTime now) {
        List<Reservation> overdue = reservationMapper.listOverdueReady(bookId, now);
        for (Reservation stale : overdue) {
            reservationMapper.updateStatus(stale.getId(), Reservation.STATUS_EXPIRED,
                    stale.getReadyAt(), stale.getHoldUntil());
            handOverReservation(bookId, now);
        }
    }

    /**
     * 还书回补库存后调用：若队列非空，队首 WAITING 置 READY 并占用一个可借名额。
     * 调用方必须已在事务内对 books 行加锁且已执行 incrementIfBelowTotal。
     */
    public void fillQueueAfterReturn(Long bookId, LocalDateTime now) {
        Reservation head = reservationMapper.findFirstWaitingByBook(bookId);
        if (head == null) {
            return;
        }
        LocalDateTime holdUntil = now.plus(props.reservation().holdHours(), ChronoUnit.HOURS);
        reservationMapper.updateStatus(head.getId(), Reservation.STATUS_READY, now, holdUntil);
        if (bookMapper.decrementIfAvailable(bookId) != 1) {
            throw new BizException(ErrorCode.BOOK_STOCK_CONFLICT);
        }
    }

    /**
     * READY 名额交接：顺延给队首 WAITING（预留转移，库存不变）；队列已空则释放库存回架。
     * 调用方必须已在事务内对 books 行加锁。
     */
    private void handOverReservation(Long bookId, LocalDateTime now) {
        Reservation next = reservationMapper.findFirstWaitingByBook(bookId);
        if (next != null) {
            LocalDateTime holdUntil = now.plus(props.reservation().holdHours(), ChronoUnit.HOURS);
            reservationMapper.updateStatus(next.getId(), Reservation.STATUS_READY, now, holdUntil);
        } else if (bookMapper.incrementIfBelowTotal(bookId) != 1) {
            throw new BizException(ErrorCode.BOOK_STOCK_CONFLICT);
        }
    }
}

package io.github.yjhhhaaa06.springbook.loan.service;

import io.github.yjhhhaaa06.springbook.auth.AccessPolicy;
import io.github.yjhhhaaa06.springbook.auth.AuthPrincipal;
import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.config.BookProperties;
import io.github.yjhhhaaa06.springbook.loan.domain.Loan;
import io.github.yjhhhaaa06.springbook.loan.dto.LoanCreateRequest;
import io.github.yjhhhaaa06.springbook.loan.dto.LoanResponse;
import io.github.yjhhhaaa06.springbook.loan.dto.LoanReturnResponse;
import io.github.yjhhhaaa06.springbook.loan.mapper.LoanMapper;
import io.github.yjhhhaaa06.springbook.reader.domain.Reader;
import io.github.yjhhhaaa06.springbook.reader.mapper.ReaderMapper;
import io.github.yjhhhaaa06.springbook.reservation.domain.Reservation;
import io.github.yjhhhaaa06.springbook.reservation.mapper.ReservationMapper;
import io.github.yjhhhaaa06.springbook.reservation.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 借还业务。
 *
 * <p>借出：锁读者行（串行化同一读者的并发借阅，固定先读者后图书避免死锁）→
 * 冻结/未缴罚款/借阅上限校验 → 对 books 行加锁（串行化同书操作）→ 惰性清理过期 READY →
 * 若读者持有未过期 READY 预约则履约借出（不再扣库存，名额已预留）；
 * 否则条件更新扣减库存（available_copies &gt; 0，0 行视为库存不足）。
 *
 * <p>归还：对 books 行加锁 → 罚款结算（不足一天计一天，封顶为该书价格）→
 * 条件回补库存 → 若预约队列非空，队首 WAITING 置 READY 并占用一个名额；
 * 逾期归还自动冻结读者（解冻见 {@link #payFine} 的自动解冻）。
 *
 * <p>缴纳罚款：仅 ADMIN；UNPAID → PAID，缴清后若读者已冻结且无待缴罚款、无逾期在借则自动解冻。
 */
@Service
public class LoanService {

    private final LoanMapper loanMapper;
    private final BookMapper bookMapper;
    private final ReaderMapper readerMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationService reservationService;
    private final AccessPolicy accessPolicy;
    private final BookProperties props;

    public LoanService(LoanMapper loanMapper, BookMapper bookMapper, ReaderMapper readerMapper,
                       ReservationMapper reservationMapper, ReservationService reservationService,
                       AccessPolicy accessPolicy, BookProperties props) {
        this.loanMapper = loanMapper;
        this.bookMapper = bookMapper;
        this.readerMapper = readerMapper;
        this.reservationMapper = reservationMapper;
        this.reservationService = reservationService;
        this.accessPolicy = accessPolicy;
        this.props = props;
    }

    @Transactional
    public LoanResponse borrow(LoanCreateRequest request, AuthPrincipal principal) {
        Long bookId = request.bookId();
        Long readerId = request.readerId();

        // 数据归属：READER 只能为自己借书，ADMIN 可代办
        accessPolicy.assertSelfOrAdmin(principal, readerId);

        // 先锁读者行：同一读者并发借阅串行化（上限/罚款校验不被竞态穿透）
        Reader reader = readerMapper.lockById(readerId);
        if (reader == null) {
            throw new BizException(ErrorCode.READER_NOT_FOUND);
        }
        if (Reader.STATUS_FROZEN.equals(reader.getStatus())) {
            throw new BizException(ErrorCode.READER_FROZEN);
        }
        if (loanMapper.countUnpaidByReader(readerId) > 0) {
            throw new BizException(ErrorCode.FINE_UNPAID);
        }
        int maxBorrowing = props.loan().maxBorrowing();
        if (maxBorrowing > 0 && loanMapper.countBorrowedByReader(readerId) >= maxBorrowing) {
            throw new BizException(ErrorCode.LOAN_LIMIT_REACHED);
        }

        Book book = bookMapper.lockById(bookId);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        reservationService.expireStaleReady(bookId, now);

        // 履约借出：读者持有该书未过期 READY 预约，名额已预留，不再扣减库存
        Reservation ready = reservationMapper.findReadyByBookAndReader(bookId, readerId, now);
        if (ready != null) {
            Loan loan = insertLoan(bookId, readerId, now);
            reservationMapper.updateStatus(ready.getId(), Reservation.STATUS_FULFILLED,
                    ready.getReadyAt(), ready.getHoldUntil());
            return LoanResponse.from(loanMapper.findById(loan.getId()));
        }

        // 普通借出：条件扣减库存，0 行即库存不足（含与预约预留的并发竞争）
        if (bookMapper.decrementIfAvailable(bookId) != 1) {
            throw new BizException(ErrorCode.BOOK_OUT_OF_STOCK);
        }
        Loan loan = insertLoan(bookId, readerId, now);
        return LoanResponse.from(loanMapper.findById(loan.getId()));
    }

    @Transactional
    public LoanReturnResponse returnBook(Long loanId, AuthPrincipal principal) {
        Loan loan = loanMapper.findById(loanId);
        if (loan == null) {
            throw new BizException(ErrorCode.LOAN_NOT_FOUND);
        }
        // 数据归属：READER 只能归还自己的借阅
        accessPolicy.assertSelfOrAdmin(principal, loan.getReaderId());
        if (Loan.STATUS_RETURNED.equals(loan.getStatus())) {
            throw new BizException(ErrorCode.LOAN_ALREADY_RETURNED);
        }
        Book book = bookMapper.lockById(loan.getBookId());

        LocalDateTime now = LocalDateTime.now();
        long overdueDays = overdueDays(loan.getDueAt(), now);
        int fine = Math.min((int) Math.min(overdueDays * props.fine().perDayCents(), Integer.MAX_VALUE),
                book.getPriceCents());

        if (loanMapper.markReturned(loanId, now, fine) != 1) {
            throw new BizException(ErrorCode.LOAN_ALREADY_RETURNED);
        }
        if (bookMapper.incrementIfBelowTotal(loan.getBookId()) != 1) {
            throw new BizException(ErrorCode.BOOK_STOCK_CONFLICT);
        }
        // 逾期归还自动冻结：冻结拦截在借出入口生效
        if (overdueDays > 0) {
            readerMapper.updateStatus(loan.getReaderId(), Reader.STATUS_FROZEN);
        }
        reservationService.fillQueueAfterReturn(loan.getBookId(), now);

        return new LoanReturnResponse(
                loanId,
                loan.getBookId(),
                loan.getReaderId(),
                loan.getBorrowedAt(),
                loan.getDueAt(),
                now,
                overdueDays,
                fine,
                BigDecimal.valueOf(fine).movePointLeft(2)
        );
    }

    /**
     * 缴纳罚款（ADMIN）：UNPAID → PAID；缴清后若读者已冻结且无待缴罚款、无逾期在借则自动解冻。
     */
    @Transactional
    public LoanResponse payFine(Long loanId) {
        Loan loan = loanMapper.findById(loanId);
        if (loan == null) {
            throw new BizException(ErrorCode.LOAN_NOT_FOUND);
        }
        if (loanMapper.markFinePaid(loanId) != 1) {
            throw new BizException(ErrorCode.FINE_NOT_UNPAID);
        }
        maybeUnfreeze(loan.getReaderId());
        return LoanResponse.from(loanMapper.findById(loanId));
    }

    public List<LoanResponse> list(Long readerId, String status, Boolean overdue, AuthPrincipal principal) {
        if (readerId != null) {
            // 数据归属：查询他人记录仅 ADMIN；READER 显式传自己的 readerId 视为合法过滤
            accessPolicy.assertSelfOrAdmin(principal, readerId);
        }
        LocalDateTime now = LocalDateTime.now();
        // READER 强制只看自己（不传 readerId 也过滤）；ADMIN 的 principal.readerId 为 null，按参数过滤
        Long effectiveReaderId = principal.readerId() != null ? principal.readerId() : readerId;
        return loanMapper.listByQuery(effectiveReaderId, status, overdue, now).stream()
                .map(loan -> LoanResponse.from(loan, now))
                .toList();
    }

    /**
     * 自动解冻：读者处于 FROZEN、无待缴罚款且无逾期在借时置回 ACTIVE。
     */
    private void maybeUnfreeze(Long readerId) {
        Reader reader = readerMapper.findById(readerId);
        if (reader == null || !Reader.STATUS_FROZEN.equals(reader.getStatus())) {
            return;
        }
        if (loanMapper.countUnpaidByReader(readerId) > 0) {
            return;
        }
        if (loanMapper.countOverdueBorrowedByReader(readerId, LocalDateTime.now()) > 0) {
            return;
        }
        readerMapper.updateStatus(readerId, Reader.STATUS_ACTIVE);
    }

    private Loan insertLoan(Long bookId, Long readerId, LocalDateTime now) {
        Loan loan = new Loan();
        loan.setBookId(bookId);
        loan.setReaderId(readerId);
        loan.setBorrowedAt(now);
        loan.setDueAt(now.plusDays(props.loan().days()));
        loan.setStatus(Loan.STATUS_BORROWED);
        loan.setFineCents(0);
        loanMapper.insert(loan);
        return loan;
    }

    /**
     * 逾期天数：不足一天计一天，向上取整；未逾期返回 0。
     */
    static long overdueDays(LocalDateTime dueAt, LocalDateTime now) {
        long seconds = Duration.between(dueAt, now).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (seconds + 86399) / 86400;
    }
}

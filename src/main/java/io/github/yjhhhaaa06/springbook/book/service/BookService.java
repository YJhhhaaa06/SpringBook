package io.github.yjhhhaaa06.springbook.book.service;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.dto.BookCreateRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookResponse;
import io.github.yjhhhaaa06.springbook.book.dto.BookUpdateRequest;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.common.PageResult;
import io.github.yjhhhaaa06.springbook.loan.mapper.LoanMapper;
import io.github.yjhhhaaa06.springbook.reservation.mapper.ReservationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 图书业务：新增、修改（乐观锁）、删除（借阅/预约占用校验）、查询与分页检索。
 */
@Service
public class BookService {

    private final BookMapper bookMapper;
    private final LoanMapper loanMapper;
    private final ReservationMapper reservationMapper;

    public BookService(BookMapper bookMapper, LoanMapper loanMapper, ReservationMapper reservationMapper) {
        this.bookMapper = bookMapper;
        this.loanMapper = loanMapper;
        this.reservationMapper = reservationMapper;
    }

    @Transactional
    public BookResponse create(BookCreateRequest req) {
        if (bookMapper.findByIsbn(req.isbn()) != null) {
            throw new BizException(ErrorCode.ISBN_DUPLICATED);
        }
        Book book = new Book();
        book.setIsbn(req.isbn());
        book.setTitle(req.title());
        book.setTotalCopies(req.totalCopies());
        book.setAvailableCopies(req.totalCopies());
        book.setPriceCents(req.priceCents());
        try {
            bookMapper.insert(book);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.ISBN_DUPLICATED);
        }
        return BookResponse.from(bookMapper.findById(book.getId()));
    }

    @Transactional
    public BookResponse update(Long id, BookUpdateRequest req) {
        Book book = bookMapper.findById(id);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        for (int i = 0; i < 3; i++) {
            int updated = bookMapper.updateTitleAndPrice(id, req.title(), req.priceCents(), book.getVersion());
            if (updated == 1) {
                return BookResponse.from(bookMapper.findById(id));
            }
            book = bookMapper.findById(id);
            if (book == null) {
                throw new BizException(ErrorCode.BOOK_NOT_FOUND);
            }
        }
        throw new BizException(ErrorCode.BOOK_VERSION_CONFLICT);
    }

    @Transactional
    public void delete(Long id) {
        Book book = bookMapper.findById(id);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        if (loanMapper.countBorrowedByBook(id) > 0) {
            throw new BizException(ErrorCode.BOOK_HAS_OPEN_LOANS);
        }
        if (reservationMapper.countActiveByBook(id) > 0) {
            throw new BizException(ErrorCode.BOOK_HAS_ACTIVE_RESERVATIONS);
        }
        bookMapper.deleteById(id);
    }

    public BookResponse getById(Long id) {
        Book book = bookMapper.findById(id);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        return BookResponse.from(book);
    }

    public PageResult<BookResponse> page(String keyword, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = bookMapper.countByKeyword(kw);
        List<BookResponse> records = bookMapper.listByKeyword(kw, (p - 1) * s, s)
                .stream()
                .map(BookResponse::from)
                .toList();
        return PageResult.of(total, p, s, records);
    }
}

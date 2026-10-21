package io.github.yjhhhaaa06.springbook.book.service;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.dto.BookCopiesRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookCreateRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookResponse;
import io.github.yjhhhaaa06.springbook.book.dto.BookSuggestion;
import io.github.yjhhhaaa06.springbook.book.dto.BookUpdateRequest;
import io.github.yjhhhaaa06.springbook.book.index.BookIndex;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.common.PageResult;
import io.github.yjhhhaaa06.springbook.loan.mapper.LoanMapper;
import io.github.yjhhhaaa06.springbook.reservation.mapper.ReservationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图书业务：新增、修改（乐观锁）、删除（借阅/预约占用校验）、查询与分页检索、
 * 入库/下架（调整总册数，下架校验在借 + 未过期 READY 预留）、书名联想。
 */
@Service
public class BookService {

    /** 联想默认/最大返回条数，与前端下拉高度匹配 */
    private static final int SUGGEST_DEFAULT_LIMIT = 8;
    private static final int SUGGEST_MAX_LIMIT = 20;

    private final BookMapper bookMapper;
    private final LoanMapper loanMapper;
    private final ReservationMapper reservationMapper;
    private final BookIndex bookIndex;

    public BookService(BookMapper bookMapper, LoanMapper loanMapper, ReservationMapper reservationMapper,
                       BookIndex bookIndex) {
        this.bookMapper = bookMapper;
        this.loanMapper = loanMapper;
        this.reservationMapper = reservationMapper;
        this.bookIndex = bookIndex;
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
        bookIndex.syncAdd(BookSuggestion.from(book));
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
                // book 仍持有旧书名，正好作为索引里要摘掉的那一条
                bookIndex.syncUpdate(BookSuggestion.from(book),
                        new BookSuggestion(id, book.getIsbn(), req.title()));
                return BookResponse.from(bookMapper.findById(id));
            }
            book = bookMapper.findById(id);
            if (book == null) {
                throw new BizException(ErrorCode.BOOK_NOT_FOUND);
            }
        }
        throw new BizException(ErrorCode.BOOK_VERSION_CONFLICT);
    }

    /**
     * 入库/下架：调整总册数。行锁内校验新值 ≥ 在借数 + 未过期 READY 预留数，
     * 可借数按差额同步增减（CHECK 约束 + 条件更新兜底）。
     */
    @Transactional
    public BookResponse adjustCopies(Long id, BookCopiesRequest req) {
        Book book = bookMapper.lockById(id);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        int newTotal = req.totalCopies();
        if (newTotal == book.getTotalCopies()) {
            return BookResponse.from(book);
        }
        long occupied = loanMapper.countBorrowedByBook(id)
                + reservationMapper.countReadyByBook(id, LocalDateTime.now());
        if (newTotal < occupied) {
            throw new BizException(ErrorCode.BOOK_COPY_UNDERFLOW);
        }
        int delta = newTotal - book.getTotalCopies();
        if (bookMapper.updateCopies(id, newTotal, delta, book.getTotalCopies()) != 1) {
            throw new BizException(ErrorCode.BOOK_STOCK_CONFLICT);
        }
        return BookResponse.from(bookMapper.findById(id));
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
        bookIndex.syncRemove(BookSuggestion.from(book));
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

    /**
     * 书名联想：纯内存前缀检索，不查库、不排序（索引本身保证字典序）。
     *
     * <p>刻意不走 {@code LIKE}：现有检索是 {@code '%kw%'}，前导通配符用不上 title 索引，
     * 逐键触发会把全表扫描打到数据库；这里一次遍历只与前缀和返回条数有关。
     *
     * @param query 已由前端 trim；空串返回空列表而不是报错
     * @param limit 可空，夹紧到 [1, 20]
     */
    public List<BookSuggestion> suggest(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int cap = limit == null || limit < 1 ? SUGGEST_DEFAULT_LIMIT : Math.min(limit, SUGGEST_MAX_LIMIT);
        return bookIndex.suggest(query, cap);
    }
}

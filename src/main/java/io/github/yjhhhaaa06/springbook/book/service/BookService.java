package io.github.yjhhhaaa06.springbook.book.service;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.dto.BookCopiesRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookCreateRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookResponse;
import io.github.yjhhhaaa06.springbook.book.dto.BookSuggestion;
import io.github.yjhhhaaa06.springbook.book.dto.BookUpdateRequest;
import io.github.yjhhhaaa06.springbook.book.index.BookIndex;
import io.github.yjhhhaaa06.springbook.book.index.BookMemoryIndex;
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
 *
 * <p><b>读走内存、写走数据库</b>：按 id / ISBN 取书命中 {@link BookMemoryIndex} 的线性表，
 * 不再访问数据库；分页模糊检索必须留在 SQL（{@code LIKE '%kw%'} + 分页 + 计数），
 * 内存表替不了它。所有写操作仍然直连数据库，索引只在事务提交后同步 —— 缓存从不参与
 * 库存扣减、乐观锁这类正确性判断，这是它敢存在的唯一理由。
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
    private final BookMemoryIndex bookMemoryIndex;

    public BookService(BookMapper bookMapper, LoanMapper loanMapper, ReservationMapper reservationMapper,
                       BookIndex bookIndex, BookMemoryIndex bookMemoryIndex) {
        this.bookMapper = bookMapper;
        this.loanMapper = loanMapper;
        this.reservationMapper = reservationMapper;
        this.bookIndex = bookIndex;
        this.bookMemoryIndex = bookMemoryIndex;
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
        // 插入后的行才有 createdAt/version，回查一次拿到完整对象；同一份数据两用：
        // 既作为响应，也作为索引条目（避免缓存里存一个字段残缺的 Book）
        Book created = bookMapper.findById(book.getId());
        bookMemoryIndex.syncAdd(created);
        return BookResponse.from(created);
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
                Book refreshed = bookMapper.findById(id);
                bookMemoryIndex.syncUpdate(refreshed);
                return BookResponse.from(refreshed);
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
        Book refreshed = bookMapper.findById(id);
        bookMemoryIndex.syncUpdate(refreshed);
        return BookResponse.from(refreshed);
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
        bookMemoryIndex.syncRemove(id);
    }

    /** 按 id 取书：命中内存线性表的「id → 下标」目录，O(1)；未命中才回源数据库。 */
    public BookResponse getById(Long id) {
        Book cached = bookMemoryIndex.findById(id);
        if (cached != null) {
            return BookResponse.from(cached);
        }
        // 未命中只会发生在启动装载完成前的几秒，或这条刚被移除。回源但不回填 ——
        // 回填会与「删除后 afterCommit 移除」形成竞态，让已删除的书重新出现。
        Book book = bookMapper.findById(id);
        if (book == null) {
            throw new BizException(ErrorCode.BOOK_NOT_FOUND);
        }
        return BookResponse.from(book);
    }

    /**
     * 按 ISBN 取书：在按 ISBN 有序的线性表上二分，O(log n)。
     * 未命中同样只回源、不回填，理由见 {@link #getById}。
     */
    public BookResponse getByIsbn(String isbn) {
        Book cached = bookMemoryIndex.findByIsbn(isbn);
        if (cached != null) {
            return BookResponse.from(cached);
        }
        Book book = bookMapper.findByIsbn(isbn);
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

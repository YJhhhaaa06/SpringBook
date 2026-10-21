package io.github.yjhhhaaa06.springbook.book.service;

import io.github.yjhhhaaa06.springbook.book.domain.Book;
import io.github.yjhhhaaa06.springbook.book.dto.HotBookResponse;
import io.github.yjhhhaaa06.springbook.book.mapper.BookMapper;
import io.github.yjhhhaaa06.springbook.common.BookHitCount;
import io.github.yjhhhaaa06.springbook.ds.TopK;
import io.github.yjhhhaaa06.springbook.loan.mapper.LoanMapper;
import io.github.yjhhhaaa06.springbook.reservation.mapper.ReservationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 热门榜：把「借阅次数」和「预约人数」两个信号合成热度，取前 k 名。
 *
 * <p><b>为什么不在一条 SQL 里做完</b>：两条聚合各自走覆盖索引、各自归属自己的模块；
 * 权重、时间窗这些口径留在代码里，改一行就能调，也要能被单元测试直接盯住。
 * 硬塞进一条 UNION 子查询，参数要写两遍、口径散在 SQL 里，review 时看不出来。
 *
 * <p><b>为什么用归并 + 堆</b>：两条聚合都按 bookId 升序返回，因此可以在一次线性扫描里
 * 合并成按 bookId 有序的候选流（{@link #merge}），再喂给容量为 k 的最小堆（{@link TopK}）：
 * 时间 O((M1+M2)·log k)，除堆以外不额外复制候选集。若先合并成 List 再 sort，
 * 时间 O(M·log M) 且要多一份 M 大小的中间集合 —— 万级数据下两者都快，
 * 这里的取舍是"只保留前 k 条"的语义本来就只需要 O(k) 空间。
 *
 * <p><b>排序口径</b>：热度 = 借阅次数 + 预约人数 × {@value #RESERVATION_WEIGHT}。
 * 预约意味着「想借但借不到」，是比一次普通借阅更强的需求信号，因此给更高权重。
 * 同分时按借阅次数、再按 id 升序，保证结果稳定（否则每次请求顺序可能抖动）。
 */
@Service
public class BookHotService {

    static final int DEFAULT_LIMIT = 8;
    static final int MAX_LIMIT = 30;
    /** 默认统计最近 30 天；0 表示不限时间窗 */
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 3650;
    static final int RESERVATION_WEIGHT = 2;

    /**
     * 「谁更热」的比较器，供 {@link TopK} 使用（它按这个比较器取最大的 k 个、返回降序）。
     *
     * <p>同分时借阅次数多的优先；仍相同则 id 小的优先 —— 用 {@code reversed()} 让"更小"
     * 在比较器语义里变成"更大"，否则 tie-break 方向与输出顺序相反，榜单会来回抖动。
     */
    static final Comparator<Candidate> HOT_ORDER = Comparator
            .comparingInt(Candidate::score)
            .thenComparingInt(Candidate::borrows)
            .thenComparing(Comparator.comparingLong(Candidate::bookId).reversed());

    private final LoanMapper loanMapper;
    private final ReservationMapper reservationMapper;
    private final BookMapper bookMapper;

    public BookHotService(LoanMapper loanMapper, ReservationMapper reservationMapper, BookMapper bookMapper) {
        this.loanMapper = loanMapper;
        this.reservationMapper = reservationMapper;
        this.bookMapper = bookMapper;
    }

    /**
     * @param limit 可空，夹紧到 [1, 30]，默认 8
     * @param days  可空，统计窗口天数，0 表示全部，默认 30
     */
    public List<HotBookResponse> hot(Integer limit, Integer days) {
        int k = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int window = days == null ? DEFAULT_DAYS : Math.max(0, Math.min(days, MAX_DAYS));
        LocalDateTime since = window == 0 ? null : LocalDateTime.now().minusDays(window);

        List<Candidate> top = TopK.select(
                merge(loanMapper.listBorrowCountsSince(since), reservationMapper.listActiveCounts()),
                k, HOT_ORDER);
        if (top.isEmpty()) {
            return List.of();
        }

        Map<Long, Book> books = bookMapper.findByIds(top.stream().map(Candidate::bookId).toList())
                .stream()
                .collect(Collectors.toMap(Book::getId, Function.identity(), (left, right) -> left));
        List<HotBookResponse> ranked = new ArrayList<>(top.size());
        for (Candidate candidate : top) {
            Book book = books.get(candidate.bookId());
            if (book == null) {
                // 计数表里存在、books 里已没有：历史残留数据，跳过而不是让整榜失败
                continue;
            }
            ranked.add(new HotBookResponse(book.getId(), book.getIsbn(), book.getTitle(),
                    candidate.borrows(), candidate.reserves(), candidate.score()));
        }
        return ranked;
    }

    /**
     * 把两条「按 bookId 升序」的计数序列归并成一条同样有序的候选流。
     * 双指针、无中间 Map：内存只随结果流走，不会因为候选多而多一份字典。
     */
    static Iterable<Candidate> merge(List<BookHitCount> borrows, List<BookHitCount> reserves) {
        return () -> new Iterator<>() {
            private int i;
            private int j;

            @Override
            public boolean hasNext() {
                return i < borrows.size() || j < reserves.size();
            }

            @Override
            public Candidate next() {
                long left = i < borrows.size() ? borrows.get(i).getBookId() : Long.MAX_VALUE;
                long right = j < reserves.size() ? reserves.get(j).getBookId() : Long.MAX_VALUE;
                if (left < right) {
                    return borrowOnly(borrows.get(i++));
                }
                if (right < left) {
                    return reserveOnly(reserves.get(j++));
                }
                if (left == Long.MAX_VALUE) {
                    throw new NoSuchElementException();
                }
                // 两侧都有这本书：合并成一条
                BookHitCount borrow = borrows.get(i++);
                BookHitCount reserve = reserves.get(j++);
                return new Candidate(borrow.getBookId(), hits(borrow), hits(reserve));
            }
        };
    }

    private static Candidate borrowOnly(BookHitCount borrow) {
        return new Candidate(borrow.getBookId(), hits(borrow), 0);
    }

    private static Candidate reserveOnly(BookHitCount reserve) {
        return new Candidate(reserve.getBookId(), 0, hits(reserve));
    }

    private static int hits(BookHitCount count) {
        return count.getHits() == null ? 0 : count.getHits();
    }

    /** 一本书的原始信号 + 折算后的热度 */
    record Candidate(long bookId, int borrows, int reserves) {

        int score() {
            return borrows + reserves * RESERVATION_WEIGHT;
        }
    }
}

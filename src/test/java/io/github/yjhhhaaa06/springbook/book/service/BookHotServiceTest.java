package io.github.yjhhhaaa06.springbook.book.service;

import io.github.yjhhhaaa06.springbook.common.BookHitCount;
import io.github.yjhhhaaa06.springbook.ds.TopK;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BookHotService} 的纯单元测试：不启 Spring、不连库，只验证归并与排序口径。
 */
class BookHotServiceTest {

    private static BookHitCount count(long bookId, int hits) {
        BookHitCount row = new BookHitCount();
        row.setBookId(bookId);
        row.setHits(hits);
        return row;
    }

    private static List<BookHotService.Candidate> drain(Iterable<BookHotService.Candidate> source) {
        List<BookHotService.Candidate> out = new ArrayList<>();
        for (BookHotService.Candidate candidate : source) {
            out.add(candidate);
        }
        return out;
    }

    @Test
    @DisplayName("两侧都有同一本书时合并成一条，不产生两条")
    void mergesOverlappingBookIds() {
        List<BookHotService.Candidate> merged = drain(BookHotService.merge(
                List.of(count(1, 3), count(3, 1)),
                List.of(count(2, 2), count(3, 4))));

        assertThat(merged).extracting(BookHotService.Candidate::bookId)
                .containsExactly(1L, 2L, 3L);
        assertThat(merged.get(2).borrows()).isEqualTo(1);
        assertThat(merged.get(2).reserves()).isEqualTo(4);
    }

    @Test
    @DisplayName("归并结果始终按 bookId 升序，任一侧为空也成立")
    void keepsAscendingOrderWhenOneSideEmpty() {
        assertThat(drain(BookHotService.merge(List.of(), List.of(count(5, 1), count(9, 2)))))
                .extracting(BookHotService.Candidate::bookId).containsExactly(5L, 9L);
        assertThat(drain(BookHotService.merge(List.of(count(4, 1)), List.of())))
                .extracting(BookHotService.Candidate::bookId).containsExactly(4L);
        assertThat(drain(BookHotService.merge(List.of(), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("热度 = 借阅次数 + 预约人数 × 2")
    void scoreWeightsReservationsHigher() {
        BookHotService.Candidate oneBorrow = new BookHotService.Candidate(1, 1, 0);
        BookHotService.Candidate oneReserve = new BookHotService.Candidate(2, 0, 1);

        assertThat(oneBorrow.score()).isEqualTo(1);
        assertThat(oneReserve.score()).isEqualTo(BookHotService.RESERVATION_WEIGHT);
        assertThat(oneReserve.score()).isGreaterThan(oneBorrow.score());
    }

    @Test
    @DisplayName("同分时按借阅次数降序，再按 id 升序，结果稳定")
    void tiesAreDeterministic() {
        BookHotService.Candidate a = new BookHotService.Candidate(9, 2, 0);   // 2
        BookHotService.Candidate b = new BookHotService.Candidate(3, 4, 0);   // 4
        BookHotService.Candidate c = new BookHotService.Candidate(1, 2, 0);   // 2，与 a 同分

        // 走真实链路（TopK 内部是容量 k 的最小堆），而不是裸 sort
        List<BookHotService.Candidate> ranked = TopK.select(List.of(a, b, c), 3, BookHotService.HOT_ORDER);

        assertThat(ranked).extracting(BookHotService.Candidate::bookId).containsExactly(3L, 1L, 9L);
    }

    @Test
    @DisplayName("候选耗尽后再取元素抛 NoSuchElementException")
    void exhaustedIteratorThrows() {
        Iterator<BookHotService.Candidate> iterator =
                BookHotService.merge(List.of(count(1, 1)), List.of()).iterator();
        assertThat(iterator.next().bookId()).isEqualTo(1L);
        assertThatThrownBy(iterator::next).isInstanceOf(java.util.NoSuchElementException.class);
    }
}

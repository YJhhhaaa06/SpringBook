package io.github.yjhhhaaa06.springbook.book.dto;

/**
 * 热门榜条目。
 *
 * <p>{@code score} 是把两个信号折算成一个数之后的排序依据（借阅次数 + 预约人数 × 2），
 * 一并返回是为了让前端/报告能解释"为什么它排在这里"，而不是只看到一个名次。
 */
public record HotBookResponse(
        Long id,
        String isbn,
        String title,
        int borrowCount,
        int reservationCount,
        int score
) {
}

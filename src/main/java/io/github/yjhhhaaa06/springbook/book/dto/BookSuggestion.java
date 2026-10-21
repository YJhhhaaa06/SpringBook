package io.github.yjhhhaaa06.springbook.book.dto;

import io.github.yjhhhaaa06.springbook.book.domain.Book;

/**
 * 检索联想条目。刻意只含<b>不随借还变化</b>的字段：
 * 联想结果完全来自内存索引，不需要回查数据库，也不会给出过期的库存数字。
 * 用户点选后走正常检索接口，由数据库给出实时的可借数量。
 */
public record BookSuggestion(Long id, String isbn, String title) {

    public static BookSuggestion from(Book book) {
        return new BookSuggestion(book.getId(), book.getIsbn(), book.getTitle());
    }
}

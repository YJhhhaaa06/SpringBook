package io.github.yjhhhaaa06.springbook.book.dto;

import io.github.yjhhhaaa06.springbook.book.domain.Book;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图书响应。priceCents 以「分」存储，price 换算为「元」仅用于展示。
 */
public record BookResponse(
        Long id,
        String isbn,
        String title,
        Integer totalCopies,
        Integer availableCopies,
        Integer priceCents,
        BigDecimal price,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getTotalCopies(),
                book.getAvailableCopies(),
                book.getPriceCents(),
                BigDecimal.valueOf(book.getPriceCents()).movePointLeft(2),
                book.getVersion(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}

package io.github.yjhhhaaa06.springbook.book.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书（对应表 books）。金额以「分」存储。
 */
@Data
public class Book {

    private Long id;
    private String isbn;
    private String title;
    private Integer totalCopies;
    private Integer availableCopies;
    private Integer priceCents;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

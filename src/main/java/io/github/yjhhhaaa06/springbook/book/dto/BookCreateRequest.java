package io.github.yjhhhaaa06.springbook.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增图书请求。
 */
public record BookCreateRequest(

        @NotBlank(message = "书号不能为空")
        @Size(max = 20, message = "书号最长 20 个字符")
        String isbn,

        @NotBlank(message = "书名不能为空")
        @Size(max = 200, message = "书名最长 200 个字符")
        String title,

        @NotNull(message = "总册数不能为空")
        @Min(value = 1, message = "总册数至少为 1")
        Integer totalCopies,

        @NotNull(message = "价格不能为空")
        @Min(value = 0, message = "价格不能为负")
        Integer priceCents
) {
}

package io.github.yjhhhaaa06.springbook.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改图书请求（全量更新书名与价格；总册数调整属入库/下架，第一版不开放）。
 */
public record BookUpdateRequest(

        @NotBlank(message = "书名不能为空")
        @Size(max = 200, message = "书名最长 200 个字符")
        String title,

        @NotNull(message = "价格不能为空")
        @Min(value = 0, message = "价格不能为负")
        Integer priceCents
) {
}

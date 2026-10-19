package io.github.yjhhhaaa06.springbook.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 调整总册数（入库/下架）请求：直接给目标值。
 * 下架校验：新总册数不得低于在借数 + 未过期 READY 预留数。
 */
public record BookCopiesRequest(

        @NotNull(message = "总册数不能为空")
        @Min(value = 0, message = "总册数不能为负")
        Integer totalCopies
) {
}

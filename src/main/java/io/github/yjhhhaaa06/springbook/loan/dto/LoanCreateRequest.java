package io.github.yjhhhaaa06.springbook.loan.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 借书请求。
 */
public record LoanCreateRequest(

        @NotNull(message = "bookId 不能为空")
        Long bookId,

        @NotNull(message = "readerId 不能为空")
        Long readerId
) {
}

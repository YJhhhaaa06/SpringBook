package io.github.yjhhhaaa06.springbook.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 还书响应：包含逾期天数与本次结算的罚款金额。
 */
public record LoanReturnResponse(
        Long id,
        Long bookId,
        Long readerId,
        LocalDateTime borrowedAt,
        LocalDateTime dueAt,
        LocalDateTime returnedAt,
        long overdueDays,
        int fineCents,
        BigDecimal fine
) {
}

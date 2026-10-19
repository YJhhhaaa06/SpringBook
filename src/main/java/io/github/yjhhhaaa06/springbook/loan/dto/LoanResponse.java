package io.github.yjhhhaaa06.springbook.loan.dto;

import io.github.yjhhhaaa06.springbook.loan.domain.Loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 借阅记录响应。fineCents 以「分」存储，fine 换算为「元」仅用于展示。
 */
public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        Long readerId,
        String readerName,
        LocalDateTime borrowedAt,
        LocalDateTime dueAt,
        LocalDateTime returnedAt,
        String status,
        Integer fineCents,
        BigDecimal fine,
        String fineStatus,
        boolean overdue
) {

    public static LoanResponse from(Loan loan) {
        return from(loan, LocalDateTime.now());
    }

    public static LoanResponse from(Loan loan, LocalDateTime now) {
        return new LoanResponse(
                loan.getId(),
                loan.getBookId(),
                loan.getBookTitle(),
                loan.getReaderId(),
                loan.getReaderName(),
                loan.getBorrowedAt(),
                loan.getDueAt(),
                loan.getReturnedAt(),
                loan.getStatus(),
                loan.getFineCents(),
                BigDecimal.valueOf(loan.getFineCents()).movePointLeft(2),
                loan.getFineStatus(),
                loan.overdue(now)
        );
    }
}

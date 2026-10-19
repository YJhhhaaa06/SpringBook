package io.github.yjhhhaaa06.springbook.loan.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 借阅记录（对应表 loans）。
 * 状态只有 BORROWED / RETURNED；「是否逾期」由 due_at 与 returned_at 实时派生，不落库。
 */
@Data
public class Loan {

    public static final String STATUS_BORROWED = "BORROWED";
    public static final String STATUS_RETURNED = "RETURNED";

    private Long id;
    private Long bookId;
    private Long readerId;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;
    private String status;
    private Integer fineCents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** join 带出的展示字段 */
    private String bookTitle;
    private String readerName;

    public boolean overdue(LocalDateTime now) {
        return STATUS_BORROWED.equals(status) && dueAt.isBefore(now);
    }
}

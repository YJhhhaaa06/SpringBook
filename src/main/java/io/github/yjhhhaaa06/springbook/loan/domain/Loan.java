package io.github.yjhhhaaa06.springbook.loan.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 借阅记录（对应表 loans）。
 * 状态只有 BORROWED / RETURNED；「是否逾期」由 due_at 与 returned_at 实时派生，不落库。
 * fine_status 独立于借阅状态：NONE（无罚款）/ UNPAID（待缴）/ PAID（已缴），见 V2 迁移。
 */
@Data
public class Loan {

    public static final String STATUS_BORROWED = "BORROWED";
    public static final String STATUS_RETURNED = "RETURNED";

    public static final String FINE_NONE = "NONE";
    public static final String FINE_UNPAID = "UNPAID";
    public static final String FINE_PAID = "PAID";

    private Long id;
    private Long bookId;
    private Long readerId;
    private LocalDateTime borrowedAt;
    private LocalDateTime dueAt;
    private LocalDateTime returnedAt;
    private String status;
    private Integer fineCents;
    private String fineStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** join 带出的展示字段 */
    private String bookTitle;
    private String readerName;

    public boolean overdue(LocalDateTime now) {
        return STATUS_BORROWED.equals(status) && dueAt.isBefore(now);
    }
}

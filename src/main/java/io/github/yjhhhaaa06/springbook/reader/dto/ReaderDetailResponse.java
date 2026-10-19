package io.github.yjhhhaaa06.springbook.reader.dto;

import io.github.yjhhhaaa06.springbook.reader.domain.Reader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 读者详情（含在借列表与未缴罚款汇总）。
 */
public record ReaderDetailResponse(
        Long id,
        String name,
        String phone,
        String status,
        LocalDateTime createdAt,
        Long unpaidFineCents,
        BigDecimal unpaidFine,
        List<OnLoanItem> onLoans
) {

    /**
     * 在借中的一项借阅。
     */
    public record OnLoanItem(
            Long loanId,
            Long bookId,
            String bookTitle,
            LocalDateTime borrowedAt,
            LocalDateTime dueAt,
            boolean overdue
    ) {
    }

    public static ReaderDetailResponse of(Reader reader, long unpaidFineCents, List<OnLoanItem> onLoans) {
        return new ReaderDetailResponse(reader.getId(), reader.getName(), reader.getPhone(),
                reader.getStatus(), reader.getCreatedAt(),
                unpaidFineCents, BigDecimal.valueOf(unpaidFineCents).movePointLeft(2), onLoans);
    }
}

package io.github.yjhhhaaa06.springbook.reader.dto;

import io.github.yjhhhaaa06.springbook.reader.domain.Reader;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 读者详情（含在借列表）。
 */
public record ReaderDetailResponse(
        Long id,
        String name,
        String phone,
        String status,
        LocalDateTime createdAt,
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

    public static ReaderDetailResponse of(Reader reader, List<OnLoanItem> onLoans) {
        return new ReaderDetailResponse(reader.getId(), reader.getName(), reader.getPhone(),
                reader.getStatus(), reader.getCreatedAt(), onLoans);
    }
}

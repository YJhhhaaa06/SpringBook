package io.github.yjhhhaaa06.springbook.reader.dto;

import io.github.yjhhhaaa06.springbook.reader.domain.Reader;

import java.time.LocalDateTime;

/**
 * 读者响应。
 */
public record ReaderResponse(
        Long id,
        String name,
        String phone,
        String status,
        LocalDateTime createdAt
) {

    public static ReaderResponse from(Reader reader) {
        return new ReaderResponse(reader.getId(), reader.getName(), reader.getPhone(),
                reader.getStatus(), reader.getCreatedAt());
    }
}

package io.github.yjhhhaaa06.springbook.reader.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 借阅人（对应表 readers）。
 */
@Data
public class Reader {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";

    private Long id;
    private String name;
    private String phone;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

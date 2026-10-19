package io.github.yjhhhaaa06.springbook.auth.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录账号（对应表 accounts）。READER 账号通过 readerId 关联读者资料，ADMIN 为 NULL。
 */
@Data
public class Account {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_READER = "READER";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private Long readerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

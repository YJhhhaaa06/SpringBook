package io.github.yjhhhaaa06.springbook.auth;

/**
 * JWT 解析后的认证主体（存入 SecurityContext）。
 */
public record AuthPrincipal(Long accountId, String username, String role, Long readerId) {
}

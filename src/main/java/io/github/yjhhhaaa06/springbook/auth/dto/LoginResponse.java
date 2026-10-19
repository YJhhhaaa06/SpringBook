package io.github.yjhhhaaa06.springbook.auth.dto;

/**
 * 登录响应：JWT 与有效期（秒）。
 */
public record LoginResponse(String token, long expiresInSeconds) {
}

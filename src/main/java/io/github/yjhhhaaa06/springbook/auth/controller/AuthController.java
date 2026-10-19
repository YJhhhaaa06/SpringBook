package io.github.yjhhhaaa06.springbook.auth.controller;

import io.github.yjhhhaaa06.springbook.auth.AuthPrincipal;
import io.github.yjhhhaaa06.springbook.auth.dto.ChangePasswordRequest;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginRequest;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginResponse;
import io.github.yjhhhaaa06.springbook.auth.dto.RegisterRequest;
import io.github.yjhhhaaa06.springbook.auth.service.AuthService;
import io.github.yjhhhaaa06.springbook.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录与注册（匿名），修改密码（已认证，修改自己的密码）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        authService.changePassword(principal.accountId(), request);
        return Result.ok(null);
    }
}

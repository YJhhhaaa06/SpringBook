package io.github.yjhhhaaa06.springbook.auth.controller;

import io.github.yjhhhaaa06.springbook.auth.dto.LoginRequest;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginResponse;
import io.github.yjhhhaaa06.springbook.auth.service.AuthService;
import io.github.yjhhhaaa06.springbook.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口（唯一的匿名业务接口）。
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
}

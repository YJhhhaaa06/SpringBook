package io.github.yjhhhaaa06.springbook.auth.service;

import io.github.yjhhhaaa06.springbook.auth.JwtService;
import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginRequest;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginResponse;
import io.github.yjhhhaaa06.springbook.auth.mapper.AccountMapper;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录：校验用户名/口令与账号状态，签发 JWT。
 * 账号不存在与口令错误统一返回「用户名或密码错误」，避免用户名枚举。
 */
@Service
public class AuthService {

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AccountMapper accountMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Account account = accountMapper.findByUsername(request.username());
        if (account == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!Account.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return new LoginResponse(jwtService.issue(account), jwtService.getExpireMinutes() * 60);
    }
}

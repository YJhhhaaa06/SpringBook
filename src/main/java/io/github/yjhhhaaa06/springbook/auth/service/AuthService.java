package io.github.yjhhhaaa06.springbook.auth.service;

import io.github.yjhhhaaa06.springbook.auth.JwtService;
import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import io.github.yjhhhaaa06.springbook.auth.dto.ChangePasswordRequest;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginRequest;
import io.github.yjhhhaaa06.springbook.auth.dto.LoginResponse;
import io.github.yjhhhaaa06.springbook.auth.dto.RegisterRequest;
import io.github.yjhhhaaa06.springbook.auth.mapper.AccountMapper;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import io.github.yjhhhaaa06.springbook.reader.domain.Reader;
import io.github.yjhhhaaa06.springbook.reader.mapper.ReaderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录：校验用户名/口令与账号状态，签发 JWT。
 * 账号不存在与口令错误统一返回「用户名或密码错误」，避免用户名枚举。
 *
 * <p>第二版新增：自助注册（建读者资料 + READER 账号，成功即签发 token）与修改密码。
 */
@Service
public class AuthService {

    private final AccountMapper accountMapper;
    private final ReaderMapper readerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AccountMapper accountMapper, ReaderMapper readerMapper,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountMapper = accountMapper;
        this.readerMapper = readerMapper;
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

    /**
     * 自助注册：username 全局唯一、phone 唯一；同一事务内建 readers + accounts，
     * 成功直接签发 token（注册即登录）。
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (accountMapper.findByUsername(request.username()) != null) {
            throw new BizException(ErrorCode.USERNAME_DUPLICATED);
        }
        if (readerMapper.findByPhone(request.phone()) != null) {
            throw new BizException(ErrorCode.READER_PHONE_DUPLICATED);
        }
        Reader reader = new Reader();
        reader.setName(request.name());
        reader.setPhone(request.phone());
        try {
            readerMapper.insert(reader);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.READER_PHONE_DUPLICATED);
        }

        Account account = new Account();
        account.setUsername(request.username());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(Account.ROLE_READER);
        account.setReaderId(reader.getId());
        account.setStatus(Account.STATUS_ACTIVE);
        try {
            accountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.USERNAME_DUPLICATED);
        }
        return new LoginResponse(jwtService.issue(account), jwtService.getExpireMinutes() * 60);
    }

    /**
     * 修改密码：校验原密码，新旧不得相同，更新散列后立即生效（无状态 JWT 下旧 token 仍有效至过期）。
     */
    @Transactional
    public void changePassword(Long accountId, ChangePasswordRequest request) {
        Account account = accountMapper.findById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.oldPassword(), account.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        if (request.newPassword().equals(request.oldPassword())) {
            throw new BizException(ErrorCode.VALIDATION_FAILED);
        }
        accountMapper.updatePassword(accountId, passwordEncoder.encode(request.newPassword()));
    }
}

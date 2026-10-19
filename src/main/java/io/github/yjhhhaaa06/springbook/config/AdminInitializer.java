package io.github.yjhhhaaa06.springbook.config;

import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import io.github.yjhhhaaa06.springbook.auth.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时保证存在一个管理员账号：accounts 表无 ADMIN 时创建。
 * 用户名 admin，密码取环境变量 ADMIN_PASSWORD（缺省 admin123），BCrypt 存储。
 * 仅用于第一版冷启动，生产环境应尽快修改口令。
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(AccountMapper accountMapper, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountMapper.countByRole(Account.ROLE_ADMIN) > 0) {
            return;
        }
        String envPassword = System.getenv("ADMIN_PASSWORD");
        String rawPassword = envPassword == null || envPassword.isBlank() ? "admin123" : envPassword;

        Account admin = new Account();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setRole(Account.ROLE_ADMIN);
        admin.setStatus(Account.STATUS_ACTIVE);
        accountMapper.insert(admin);
        log.warn("已创建默认管理员账号 admin（密码来自 ADMIN_PASSWORD 环境变量{}），请尽快修改",
                envPassword == null || envPassword.isBlank() ? "缺省 admin123" : "");
    }
}

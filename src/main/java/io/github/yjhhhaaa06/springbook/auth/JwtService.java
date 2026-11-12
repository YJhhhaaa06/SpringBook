package io.github.yjhhhaaa06.springbook.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import io.github.yjhhhaaa06.springbook.config.BookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JWT 签发与校验（HS256，无状态）。
 * 密钥优先从 JWT_SECRET 注入；未配置时回退到内置演示密钥并打印 WARN，
 * 目的是让拿到 jar 的人不必先学会设环境变量就能跑起来。正式部署务必覆盖。
 * 载荷：sub(accountId)、username、role、readerId、iat、exp。
 */
@Component
public class JwtService {

    /** 内置演示密钥：仅用于本地/课程场景，正式部署必须用 JWT_SECRET 覆盖 */
    private static final String DEMO_SECRET = "springbook-demo-secret-change-me-in-production-32";
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expireMinutes;

    public JwtService(BookProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            secret = DEMO_SECRET;
            log.warn("未设置 JWT_SECRET，已使用内置演示密钥启动；正式部署请改用环境变量覆盖");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.expireMinutes = props.jwt().expireMinutes() <= 0 ? 120 : props.jwt().expireMinutes();
    }

    public String issue(Account account) {
        Instant now = Instant.now();
        var builder = JWT.create()
                .withSubject(String.valueOf(account.getId()))
                .withClaim("username", account.getUsername())
                .withClaim("role", account.getRole())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(expireMinutes, ChronoUnit.MINUTES));
        if (account.getReaderId() != null) {
            builder.withClaim("readerId", account.getReaderId());
        }
        return builder.sign(algorithm);
    }

    /** 校验令牌（签名 + 过期）；无效返回 null */
    public DecodedJWT verify(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }
}

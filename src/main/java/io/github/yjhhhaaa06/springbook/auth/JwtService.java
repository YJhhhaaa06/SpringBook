package io.github.yjhhhaaa06.springbook.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import io.github.yjhhhaaa06.springbook.config.BookProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JWT 签发与校验（HS256，无状态）。
 * 密钥只从环境变量 JWT_SECRET 注入（application.yaml 占位），缺失时启动失败（fail-fast）。
 * 载荷：sub(accountId)、username、role、readerId、iat、exp。
 */
@Component
public class JwtService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expireMinutes;

    public JwtService(BookProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少 JWT_SECRET 环境变量，拒绝启动（fail-fast）");
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

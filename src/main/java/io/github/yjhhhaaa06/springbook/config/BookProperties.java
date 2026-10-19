package io.github.yjhhhaaa06.springbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务参数（application.yaml 中 book.* 前缀）。
 */
@ConfigurationProperties(prefix = "book")
public record BookProperties(Loan loan, Fine fine, Reservation reservation, Jwt jwt) {

    public BookProperties {
        if (loan == null) {
            loan = new Loan(30, 5);
        }
        if (fine == null) {
            fine = new Fine(50);
        }
        if (reservation == null) {
            reservation = new Reservation(48);
        }
        if (jwt == null) {
            jwt = new Jwt(null, 120);
        }
    }

    /** 借阅参数：期限（天）与同时在借上限（本），上限 ≤ 0 视为不限制 */
    public record Loan(int days, int maxBorrowing) {
    }

    /** 罚款单价（分/天） */
    public record Fine(int perDayCents) {
    }

    /** 预约到书后的保留时长（小时） */
    public record Reservation(int holdHours) {
    }

    /** JWT 配置：secret 只从环境变量 JWT_SECRET 注入，缺失时启动失败 */
    public record Jwt(String secret, int expireMinutes) {
    }
}

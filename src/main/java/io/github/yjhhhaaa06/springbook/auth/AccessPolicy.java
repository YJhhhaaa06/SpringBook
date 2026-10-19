package io.github.yjhhhaaa06.springbook.auth;

import io.github.yjhhhaaa06.springbook.auth.domain.Account;
import io.github.yjhhhaaa06.springbook.common.BizException;
import io.github.yjhhhaaa06.springbook.common.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 数据归属策略：READER 只能操作自身资源，ADMIN 可代办任意读者。
 * 业务层校验（而非 URL 规则），因为归属判定依赖业务数据中的 readerId。
 */
@Component
public class AccessPolicy {

    /**
     * 断言当前用户是 ADMIN，或操作对象就是自己的 readerId；否则抛 40300。
     */
    public void assertSelfOrAdmin(AuthPrincipal principal, Long readerId) {
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (Account.ROLE_ADMIN.equals(principal.role())) {
            return;
        }
        if (principal.readerId() == null || !principal.readerId().equals(readerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}

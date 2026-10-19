package io.github.yjhhhaaa06.springbook.auth;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册与改密集成测试（第二版）：注册即发 token 且可立即借书、唯一性校验、改密生效。
 */
class AuthV2IntegrationTest extends IntegrationTestBase {

    @Test
    void registerCreatesAccountAndTokenWorksImmediately() {
        long bookId = createBook("978-7-144-33001-5", "注册即借书", 2, 1000);

        IntegrationTestBase.ReaderAccount account =
                registerReader("reg_ok_user", "pass-123456", "注册成功者", "13880000001");

        // 新 token 可查、可为自己借书（READER 归属校验通过）
        assertThat(get("/api/books", account.token()).code()).isZero();
        ApiResult borrow = post("/api/loans",
                Map.of("bookId", bookId, "readerId", account.readerId()), account.token());
        assertThat(borrow.code()).isZero();

        // 读者资料已建（ADMIN 视角可见）
        ApiResult detail = get("/api/readers/{id}", adminToken, account.readerId());
        assertThat(detail.code()).isZero();
        assertThat(asMap(detail.data()).get("phone")).isEqualTo("13880000001");
    }

    @Test
    void registerDuplicateUsernameRejected() {
        registerReader("reg_dup_user", "pass-123456", "占用用户名", "13880000002");

        ApiResult again = post("/api/auth/register",
                Map.of("username", "reg_dup_user", "password", "pass-123456",
                        "name", "撞名者", "phone", "13880000003"), null);
        assertThat(again.code()).isEqualTo(40915);
    }

    @Test
    void registerDuplicatePhoneRejected() {
        registerReader("reg_phone_user", "pass-123456", "占用手机号", "13880000004");

        ApiResult again = post("/api/auth/register",
                Map.of("username", "reg_phone_other", "password", "pass-123456",
                        "name", "撞号者", "phone", "13880000004"), null);
        assertThat(again.code()).isEqualTo(40904);
    }

    @Test
    void registerWeakPasswordRejected() {
        ApiResult result = post("/api/auth/register",
                Map.of("username", "reg_weak_user", "password", "12345",
                        "name", "弱密码", "phone", "13880000005"), null);
        assertThat(result.code()).isEqualTo(40001);
    }

    @Test
    void changePasswordMakesOldInvalidNewValid() {
        IntegrationTestBase.ReaderAccount account =
                registerReader("reg_chg_user", "old-pass-123", "改密者", "13880000006");

        ApiResult change = put("/api/auth/password",
                Map.of("oldPassword", "old-pass-123", "newPassword", "new-pass-456"), account.token());
        assertThat(change.code()).isZero();

        // 旧口令登录失败，新口令成功
        assertThat(post("/api/auth/login", Map.of("username", "reg_chg_user", "password", "old-pass-123"), null)
                .code()).isEqualTo(40101);
        assertThat(post("/api/auth/login", Map.of("username", "reg_chg_user", "password", "new-pass-456"), null)
                .code()).isZero();
    }

    @Test
    void changePasswordWrongOldRejected() {
        IntegrationTestBase.ReaderAccount account =
                registerReader("reg_wrong_user", "right-pass-123", "错旧密码", "13880000007");

        ApiResult change = put("/api/auth/password",
                Map.of("oldPassword", "wrong-pass", "newPassword", "new-pass-456"), account.token());
        assertThat(change.code()).isEqualTo(40002);
    }

    @Test
    void changePasswordSameAsOldRejected() {
        IntegrationTestBase.ReaderAccount account =
                registerReader("reg_same_user", "same-pass-123", "同密码", "13880000008");

        ApiResult change = put("/api/auth/password",
                Map.of("oldPassword", "same-pass-123", "newPassword", "same-pass-123"), account.token());
        assertThat(change.code()).isEqualTo(40001);
    }
}

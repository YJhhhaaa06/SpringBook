package io.github.yjhhhaaa06.springbook.auth;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 鉴权链路集成测试：登录、错误口令、匿名 401、READER 权限 403、伪造 token。
 */
class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void loginOk() {
        ApiResult result = post("/api/auth/login", Map.of("username", "admin", "password", "admin123"), null);
        assertThat(result.code()).isZero();
        Map<String, Object> data = asMap(result.data());
        assertThat((String) data.get("token")).isNotBlank();
        assertThat(longOf(data, "expiresInSeconds")).isEqualTo(7200);
    }

    @Test
    void loginWrongPassword() {
        ApiResult result = post("/api/auth/login", Map.of("username", "admin", "password", "wrong"), null);
        assertThat(result.code()).isEqualTo(40101);
    }

    @Test
    void anonymousRejected() {
        ApiResult result = get("/api/books", null);
        assertThat(result.code()).isEqualTo(40100);
    }

    @Test
    void garbageTokenRejected() {
        ApiResult result = get("/api/books", "not-a-real-token");
        assertThat(result.code()).isEqualTo(40100);
    }

    @Test
    void healthEndpointAnonymous() {
        // /actuator/health 返回 {"status":"UP"}，不是 Result 结构，直接断言原始体
        org.springframework.http.ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void readerForbiddenOnAdminWrites() {
        long readerId = createReader("普通读者", "13600000001");
        jdbc.update(
                "INSERT INTO accounts (username, password_hash, role, reader_id, status) VALUES (?, ?, 'READER', ?, 'ACTIVE')",
                "reader001", passwordEncoder.encode("reader-pass"), readerId);

        ApiResult login = post("/api/auth/login", Map.of("username", "reader001", "password", "reader-pass"), null);
        assertThat(login.code()).isZero();
        String readerToken = (String) asMap(login.data()).get("token");

        // 查询放行
        assertThat(get("/api/books", readerToken).code()).isZero();

        // 管理动作拒绝
        ApiResult create = post("/api/books",
                Map.of("isbn", "978-7-000-00001-9", "title", "越权", "totalCopies", 1, "priceCents", 100),
                readerToken);
        assertThat(create.code()).isEqualTo(40300);

        ApiResult createReader = post("/api/readers", Map.of("name", "越权", "phone", "13600000002"), readerToken);
        assertThat(createReader.code()).isEqualTo(40300);
    }
}

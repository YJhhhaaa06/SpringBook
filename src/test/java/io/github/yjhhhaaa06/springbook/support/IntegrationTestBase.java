package io.github.yjhhhaaa06.springbook.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 集成测试基类：Testcontainers 拉起真实 MySQL 8.4，Flyway 在容器内执行迁移，
 * 走真实 HTTP 端口（完整 Security 过滤链）。
 * 与 Result { code, message, data } 对齐的轻量响应体 ApiResult 便于断言。
 * HTTP 客户端用 RestTemplate + 自定义 errorHandler（4xx/5xx 不抛异常，交由用例断言）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "book.jwt.secret=test-only-secret-0123456789abcdef-0123456789abcdef")
public abstract class IntegrationTestBase {

    /** 与 Result 对齐的轻量响应体 */
    public record ApiResult(int code, String message, Object data) {
    }

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected RestTemplate rest;

    protected String adminToken;

    @BeforeEach
    void initClientAndLogin() {
        if (rest == null) {
            RestTemplate template = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
            template.setErrorHandler(new ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;
                }

                @Override
                public void handleError(java.net.URI uri, HttpMethod method, ClientHttpResponse response) {
                    // 不抛异常，由用例自行断言状态码与响应体
                }
            });
            rest = template;
        }
        ApiResult result = post("/api/auth/login", Map.of("username", "admin", "password", "admin123"), null);
        org.assertj.core.api.Assertions.assertThat(result.code()).isZero();
        adminToken = (String) asMap(result.data()).get("token");
    }

    // ---------- HTTP 辅助 ----------

    protected ApiResult post(String url, Object body, String token, Object... uriVars) {
        return exchange(url, HttpMethod.POST, body, token, uriVars);
    }

    protected ApiResult patch(String url, Object body, String token, Object... uriVars) {
        return exchange(url, HttpMethod.PATCH, body, token, uriVars);
    }

    protected ApiResult put(String url, Object body, String token, Object... uriVars) {
        return exchange(url, HttpMethod.PUT, body, token, uriVars);
    }

    protected ApiResult get(String url, String token, Object... uriVars) {
        return exchange(url, HttpMethod.GET, null, token, uriVars);
    }

    protected ApiResult delete(String url, String token, Object... uriVars) {
        return exchange(url, HttpMethod.DELETE, null, token, uriVars);
    }

    private ApiResult exchange(String url, HttpMethod method, Object body, String token, Object... uriVars) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String json = body == null ? null : objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = rest.exchange("http://localhost:" + port + url, method,
                new HttpEntity<>(json, headers), String.class, uriVars);
        String payload = response.getBody();
        if (payload == null || payload.isBlank()) {
            System.out.println("[exchange] EMPTY body: status=" + response.getStatusCode()
                    + " content-type=" + response.getHeaders().getContentType()
                    + " allow=" + response.getHeaders().getAllow());
            return new ApiResult(response.getStatusCode().value(), "", null);
        }
        return objectMapper.readValue(payload, ApiResult.class);
    }

    // ---------- 数据辅助 ----------

    @SuppressWarnings("unchecked")
    protected Map<String, Object> asMap(Object data) {
        return (Map<String, Object>) data;
    }

    @SuppressWarnings("unchecked")
    protected java.util.List<Map<String, Object>> asList(Object data) {
        return (java.util.List<Map<String, Object>>) data;
    }

    protected long longOf(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }

    protected long createBook(String isbn, String title, int totalCopies, int priceCents) {
        ApiResult result = post("/api/books",
                Map.of("isbn", isbn, "title", title, "totalCopies", totalCopies, "priceCents", priceCents),
                adminToken);
        org.assertj.core.api.Assertions.assertThat(result.code()).isZero();
        return longOf(asMap(result.data()), "id");
    }

    protected long createReader(String name, String phone) {
        ApiResult result = post("/api/readers", Map.of("name", name, "phone", phone), adminToken);
        org.assertj.core.api.Assertions.assertThat(result.code()).isZero();
        return longOf(asMap(result.data()), "id");
    }

    /** 注册账号响应：readerId 经库表反查（token 载荷不含 readerId 的明文出口） */
    public record ReaderAccount(long accountId, long readerId, String token) {
    }

    /** 走自助注册接口建 READER 账号，返回可用于调用业务接口的 token */
    protected ReaderAccount registerReader(String username, String password, String name, String phone) {
        ApiResult result = post("/api/auth/register",
                Map.of("username", username, "password", password, "name", name, "phone", phone), null);
        org.assertj.core.api.Assertions.assertThat(result.code()).isZero();
        String token = (String) asMap(result.data()).get("token");
        Long readerId = jdbc.queryForObject(
                "SELECT reader_id FROM accounts WHERE username = ?", Long.class, username);
        Long accountId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE username = ?", Long.class, username);
        return new ReaderAccount(accountId, readerId, token);
    }

    /** 直查读者状态（冻结/解冻断言用） */
    protected String readerStatus(long readerId) {
        return jdbc.queryForObject("SELECT status FROM readers WHERE id = ?", String.class, readerId);
    }

    protected long borrow(long bookId, long readerId) {
        ApiResult result = post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken);
        org.assertj.core.api.Assertions.assertThat(result.code()).isZero();
        return longOf(asMap(result.data()), "id");
    }

    protected void setDueAt(long loanId, LocalDateTime dueAt) {
        jdbc.update("UPDATE loans SET due_at = ? WHERE id = ?", java.sql.Timestamp.valueOf(dueAt), loanId);
    }

    protected int availableCopies(long bookId) {
        ApiResult result = get("/api/books/" + bookId, adminToken);
        org.assertj.core.api.Assertions.assertThat(result.code()).isZero();
        return ((Number) asMap(result.data()).get("availableCopies")).intValue();
    }
}

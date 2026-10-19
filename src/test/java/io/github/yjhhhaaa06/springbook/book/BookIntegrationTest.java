package io.github.yjhhhaaa06.springbook.book;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 图书模块集成测试：增删改查、唯一约束、校验、删除占用保护、分页检索。
 */
class BookIntegrationTest extends IntegrationTestBase {

    @Test
    void createAndFetch() {
        long id = createBook("978-7-111-00001-1", "深入理解计算机系统", 3, 13900);

        ApiResult result = get("/api/books/" + id, adminToken);
        assertThat(result.code()).isZero();
        Map<String, Object> book = asMap(result.data());
        assertThat(book.get("isbn")).isEqualTo("978-7-111-00001-1");
        assertThat(book.get("title")).isEqualTo("深入理解计算机系统");
        assertThat(longOf(book, "totalCopies")).isEqualTo(3);
        assertThat(longOf(book, "availableCopies")).isEqualTo(3);
        assertThat(longOf(book, "priceCents")).isEqualTo(13900);
        assertThat(((Number) book.get("price")).doubleValue()).isEqualTo(139.0);
        assertThat(book.get("version")).isEqualTo(0);
    }

    @Test
    void duplicateIsbnRejected() {
        createBook("978-7-111-00002-2", "Effective Java", 1, 9900);
        ApiResult result = post("/api/books",
                Map.of("isbn", "978-7-111-00002-2", "title", "另一本", "totalCopies", 1, "priceCents", 100),
                adminToken);
        assertThat(result.code()).isEqualTo(40901);
    }

    @Test
    void updateTitleAndPriceBumpsVersion() {
        long id = createBook("978-7-111-00003-3", "旧书名", 2, 5000);

        ApiResult result = put("/api/books/" + id,
                Map.of("title", "新书名", "priceCents", 6000), adminToken);
        assertThat(result.code()).isZero();
        Map<String, Object> book = asMap(result.data());
        assertThat(book.get("title")).isEqualTo("新书名");
        assertThat(longOf(book, "priceCents")).isEqualTo(6000);
        assertThat(book.get("version")).isEqualTo(1);
    }

    @Test
    void validationFailureReturnsFieldErrors() {
        ApiResult result = post("/api/books",
                Map.of("title", "", "totalCopies", 0, "priceCents", -1), adminToken);
        assertThat(result.code()).isEqualTo(40001);
        Map<String, Object> errors = asMap(result.data());
        assertThat(errors).containsKeys("isbn", "title", "totalCopies", "priceCents");
    }

    @Test
    void deleteBlockedWhenBorrowed() {
        long bookId = createBook("978-7-111-00004-4", "被借走的书", 1, 1000);
        long readerId = createReader("张三", "13800000001");
        borrow(bookId, readerId);

        ApiResult result = delete("/api/books/" + bookId, adminToken);
        assertThat(result.code()).isEqualTo(40902);
    }

    @Test
    void deleteSucceedsWhenIdle() {
        long bookId = createBook("978-7-111-00005-5", "闲置的书", 1, 1000);
        ApiResult result = delete("/api/books/" + bookId, adminToken);
        assertThat(result.code()).isZero();
        assertThat(get("/api/books/" + bookId, adminToken).code()).isEqualTo(40401);
    }

    @Test
    void searchByKeywordPaged() {
        createBook("978-7-116-00006-6", "微服务架构设计模式 A", 1, 100);
        createBook("978-7-116-00007-7", "微服务架构设计模式 B", 1, 100);
        createBook("978-7-116-00008-8", "微服务治理实战", 1, 100);
        createBook("978-7-116-00009-9", "无关的书", 1, 100);

        ApiResult page1 = get("/api/books?keyword={kw}&page={p}&size={s}",
                adminToken, "微服务", 1, 2);
        assertThat(page1.code()).isZero();
        Map<String, Object> page = asMap(page1.data());
        assertThat(longOf(page, "total")).isEqualTo(3);
        assertThat(page.get("page")).isEqualTo(1);
        assertThat(asList(page.get("records"))).hasSize(2);

        ApiResult page2 = get("/api/books?keyword={kw}&page={p}&size={s}",
                adminToken, "微服务", 2, 2);
        assertThat(asList(asMap(page2.data()).get("records"))).hasSize(1);

        ApiResult byIsbn = get("/api/books?keyword={kw}", adminToken, "978-7-116-00009-9");
        assertThat(longOf(asMap(byIsbn.data()), "total")).isEqualTo(1);
    }
}

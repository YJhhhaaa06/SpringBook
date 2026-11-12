package io.github.yjhhhaaa06.springbook.book;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 图书内存索引的集成测试：走真实 HTTP + 真实 MySQL，验证两件事 ——
 * <ul>
 *   <li>按书号（ISBN）与按 id 两条读路径确实由 {@code BookMemoryIndex} 回答；</li>
 *   <li>新增、改名、删除、借还之后，索引里的库存与数据库逐条一致。</li>
 * </ul>
 *
 * <p>为什么断言要盯着「缓存 == 库」而不是「接口返回 200」：缓存出错的典型方式不是抛异常，
 * 而是安静地返回一个过期的数字。只有拿库里的值做对照，才能发现 afterCommit 漏挂了一条路径。
 *
 * <p>每个用例用互不重叠的 ISBN 前缀，避免与其它测试类共享的库数据互相干扰。
 */
class BookMemoryIndexIntegrationTest extends IntegrationTestBase {

    @Test
    void isbnLookupReturnsFullBook() {
        long id = createBook("978-7-150-00001-1", "ZZMem 按书号查书", 3, 4500);

        ApiResult result = get("/api/books/isbn/{isbn}", adminToken, "978-7-150-00001-1");

        assertThat(result.code()).isZero();
        Map<String, Object> book = asMap(result.data());
        assertThat(longOf(book, "id")).isEqualTo(id);
        assertThat(book.get("title")).isEqualTo("ZZMem 按书号查书");
        assertThat(book.get("totalCopies")).isEqualTo(3);
        assertThat(book.get("availableCopies")).isEqualTo(3);
        assertThat(book.get("priceCents")).isEqualTo(4500);
    }

    @Test
    void renameIsVisibleThroughBothLookups() {
        long id = createBook("978-7-150-00002-2", "ZZMem 旧书名", 1, 100);

        ApiResult updated = put("/api/books/" + id,
                Map.of("title", "ZZMem 新书名", "priceCents", 200), adminToken);
        assertThat(updated.code()).isZero();

        // 两条读路径都由同一个索引回答，改名后不能只更新其中一个
        assertThat(asMap(get("/api/books/isbn/{isbn}", adminToken, "978-7-150-00002-2").data())
                .get("title")).isEqualTo("ZZMem 新书名");
        assertThat(asMap(get("/api/books/" + id, adminToken).data())
                .get("title")).isEqualTo("ZZMem 新书名");
    }

    @Test
    void deleteRemovesEntryFromIndex() {
        long id = createBook("978-7-150-00003-3", "ZZMem 待删除", 1, 100);

        assertThat(delete("/api/books/" + id, adminToken).code()).isZero();

        assertThat(get("/api/books/isbn/{isbn}", adminToken, "978-7-150-00003-3").code()).isEqualTo(40401);
        assertThat(get("/api/books/" + id, adminToken).code()).isEqualTo(40401);
    }

    @Test
    void unknownIsbnReturnsNotFound() {
        ApiResult result = get("/api/books/isbn/{isbn}", adminToken, "978-7-150-99999-9");
        assertThat(result.code()).isEqualTo(40401);
    }

    @Test
    void cachedStockMatchesDatabaseAcrossBorrowAndReturn() {
        long bookId = createBook("978-7-150-00004-4", "ZZMem 库存一致性", 2, 100);
        long readerId = createReader("ZZMem 读者", "13900015004");

        assertIndexMatchesDatabase(bookId);

        long loanId = borrow(bookId, readerId);
        assertIndexMatchesDatabase(bookId);

        assertThat(post("/api/loans/" + loanId + "/return", null, adminToken).code()).isZero();
        assertIndexMatchesDatabase(bookId);
    }

    /** 两条读路径（id / ISBN）返回的库存都必须等于库里的值 */
    private void assertIndexMatchesDatabase(long bookId) {
        Integer inDb = jdbc.queryForObject(
                "SELECT available_copies FROM books WHERE id = ?", Integer.class, bookId);
        String isbn = jdbc.queryForObject(
                "SELECT isbn FROM books WHERE id = ?", String.class, bookId);

        assertThat(availableCopies(bookId)).isEqualTo(inDb);
        Map<String, Object> byIsbn = asMap(get("/api/books/isbn/{isbn}", adminToken, isbn).data());
        assertThat(((Number) byIsbn.get("availableCopies")).intValue()).isEqualTo(inDb);
    }
}

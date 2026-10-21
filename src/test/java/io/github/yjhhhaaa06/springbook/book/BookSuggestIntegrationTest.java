package io.github.yjhhhaaa06.springbook.book;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 书名联想集成测试：走真实 HTTP + 真实 MySQL，重点验证三件事 ——
 * <ul>
 *   <li>前缀检索确实只匹配开头（与 {@code LIKE '%kw%'} 的包含语义不同）；</li>
 *   <li>写路径（新增/改名/删除）在<b>事务提交后</b>把索引同步正确，没有脏条目；</li>
 *   <li>limit 生效、空查询不报错。</li>
 * </ul>
 * 每个用例用互不重叠的书名前缀，避免与其它测试类共享的库数据互相干扰。
 */
class BookSuggestIntegrationTest extends IntegrationTestBase {

    @Test
    void prefixMatchesOnlyAtStart() {
        createBook("978-7-119-10001-1", "ZZSugA 深入理解计算机系统", 1, 100);
        createBook("978-7-119-10002-2", "ZZSugA 算法导论", 1, 100);
        createBook("978-7-119-10003-3", "深入 ZZSugA 后缀命中不算", 1, 100);

        ApiResult result = get("/api/books/suggest?q={q}", adminToken, "ZZSugA");
        assertThat(result.code()).isZero();

        List<Map<String, Object>> items = asList(result.data());
        assertThat(items).hasSize(2);
        assertThat(items).extracting(item -> item.get("title"))
                .containsExactly("ZZSugA 深入理解计算机系统", "ZZSugA 算法导论");
        assertThat(items.getFirst()).containsKeys("id", "isbn", "title");
        // 联想刻意不返回库存，避免给出会过期的数字
        assertThat(items.getFirst()).doesNotContainKey("availableCopies");
    }

    @Test
    void limitCapsResultSize() {
        for (int i = 1; i <= 3; i++) {
            createBook("978-7-119-1100" + i + "-0", "ZZSugB 第" + i + "本", 1, 100);
        }

        ApiResult result = get("/api/books/suggest?q={q}&limit={l}", adminToken, "ZZSugB", 2);
        assertThat(result.code()).isZero();
        assertThat(asList(result.data())).hasSize(2);
    }

    @Test
    void renameMovesToNewPrefix() {
        long id = createBook("978-7-119-10021-1", "ZZSugC 旧书名", 1, 100);

        ApiResult updated = put("/api/books/" + id, Map.of("title", "ZZSugD 新书名", "priceCents", 100),
                adminToken);
        assertThat(updated.code()).isZero();

        assertThat(asList(get("/api/books/suggest?q={q}", adminToken, "ZZSugC").data())).isEmpty();
        List<Map<String, Object>> hits = asList(get("/api/books/suggest?q={q}", adminToken, "ZZSugD").data());
        assertThat(hits).hasSize(1);
        assertThat(longOf(hits.getFirst(), "id")).isEqualTo(id);
    }

    @Test
    void deleteRemovesFromIndex() {
        long id = createBook("978-7-119-10031-1", "ZZSugE 待删的书", 1, 100);
        assertThat(asList(get("/api/books/suggest?q={q}", adminToken, "ZZSugE").data())).hasSize(1);

        assertThat(delete("/api/books/" + id, adminToken).code()).isZero();

        assertThat(asList(get("/api/books/suggest?q={q}", adminToken, "ZZSugE").data())).isEmpty();
    }

    @Test
    void queryIsCaseAndSpaceInsensitive() {
        createBook("978-7-119-10041-1", "ZZSugF  Java   编程思想", 1, 100);

        assertThat(asList(get("/api/books/suggest?q={q}", adminToken, "zzsugf java").data())).hasSize(1);
    }

    @Test
    void blankQueryReturnsEmptyWithoutError() {
        ApiResult noParam = get("/api/books/suggest", adminToken);
        assertThat(noParam.code()).isZero();
        assertThat(asList(noParam.data())).isEmpty();

        ApiResult blank = get("/api/books/suggest?q={q}", adminToken, "   ");
        assertThat(blank.code()).isZero();
        assertThat(asList(blank.data())).isEmpty();
    }

    @Test
    void unknownPrefixReturnsEmpty() {
        ApiResult result = get("/api/books/suggest?q={q}", adminToken, "ZZSugNoSuchPrefix");
        assertThat(result.code()).isZero();
        assertThat(asList(result.data())).isEmpty();
    }
}

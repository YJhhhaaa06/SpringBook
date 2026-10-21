package io.github.yjhhhaaa06.springbook.book;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 热门榜集成测试：走真实 HTTP + 真实 MySQL。
 *
 * <p>库里还有其它测试类造的数据，因此断言只做**相对顺序**（我造的书谁在谁前面），
 * 不断言全库排名 —— 那种断言会因为别的用例多借一次书就碎掉。
 */
class BookHotIntegrationTest extends IntegrationTestBase {

    private List<Map<String, Object>> hot(String query) {
        ApiResult result = get("/api/books/hot" + query, adminToken);
        assertThat(result.code()).isZero();
        return asList(result.data());
    }

    private static int indexOfTitle(List<Map<String, Object>> items, String title) {
        for (int i = 0; i < items.size(); i++) {
            if (title.equals(items.get(i).get("title"))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void reservationOutweighsPlainBorrows() {
        long busy = createBook("978-7-120-20001-1", "ZZHotA 借得多的书", 6, 100);
        long queued = createBook("978-7-120-20002-2", "ZZHotB 排队多的书", 1, 100);
        long borrower = createReader("热甲", "13900000101");
        long second = createReader("热乙", "13900000102");
        long third = createReader("热丙", "13900000103");

        for (int i = 0; i < 4; i++) {
            borrow(busy, borrower);                 // 4 次借阅 → 热度 4
        }
        borrow(queued, second);                     // 借空
        assertThat(post("/api/reservations",
                Map.of("bookId", queued, "readerId", borrower), adminToken).code()).isZero();
        assertThat(post("/api/reservations",
                Map.of("bookId", queued, "readerId", third), adminToken).code()).isZero();
        // 排队书：1 次借阅 + 2 人预约 → 热度 1 + 2*2 = 5

        List<Map<String, Object>> items = hot("?limit=30&days=0");
        int queuedIndex = indexOfTitle(items, "ZZHotB 排队多的书");
        int busyIndex = indexOfTitle(items, "ZZHotA 借得多的书");

        assertThat(queuedIndex).as("排队书应上榜").isNotNegative();
        assertThat(busyIndex).as("借阅书应上榜").isNotNegative();
        assertThat(queuedIndex).as("预约权重更高，应排在借阅次数更多的书之前")
                .isLessThan(busyIndex);

        Map<String, Object> row = items.get(queuedIndex);
        assertThat(longOf(row, "borrowCount")).isEqualTo(1);
        assertThat(longOf(row, "reservationCount")).isEqualTo(2);
        assertThat(longOf(row, "score")).isEqualTo(5);
        assertThat(row).containsKeys("id", "isbn", "title");
    }

    @Test
    void limitCapsResultSize() {
        // 每个用例自己造数据：整个测试库是空的，不能指望别的用例留下的借阅
        long reader = createReader("热戊", "13900000105");
        for (int i = 0; i < 3; i++) {
            long book = createBook("978-7-120-2003" + i + "-1", "ZZHotE 限量用书" + i, 1, 100);
            borrow(book, reader);
        }

        assertThat(hot("?limit=1&days=0")).hasSize(1);
        assertThat(hot("?limit=3&days=0")).hasSize(3);
        assertThat(hot("?days=0")).hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void budgetWindowExcludesOldBorrows() {
        long old = createBook("978-7-120-20021-1", "ZZHotD 陈年借阅", 1, 100);
        long reader = createReader("热丁", "13900000104");
        long loanId = borrow(old, reader);
        jdbc.update("UPDATE loans SET borrowed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(100)), loanId);

        assertThat(indexOfTitle(hot("?limit=30&days=30"), "ZZHotD 陈年借阅"))
                .as("30 天窗口内不应出现百天前的借阅").isNegative();
        assertThat(indexOfTitle(hot("?limit=30&days=0"), "ZZHotD 陈年借阅"))
                .as("days=0 表示全部时间，应重新上榜").isNotNegative();
    }
}

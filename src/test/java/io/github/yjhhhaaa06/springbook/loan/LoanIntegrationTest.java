package io.github.yjhhhaaa06.springbook.loan;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 借还闭环集成测试：库存扣减/回补、逾期罚款（向上取整、封顶）、并发抢最后一本、记录查询。
 */
class LoanIntegrationTest extends IntegrationTestBase {

    @Test
    void borrowAndReturnFlow() {
        long bookId = createBook("978-7-121-00010-1", "借还闭环", 2, 5000);
        long readerId = createReader("借书人甲", "13900000001");

        long loanId = borrow(bookId, readerId);
        assertThat(availableCopies(bookId)).isEqualTo(1);

        ApiResult result = post("/api/loans/" + loanId + "/return", null, adminToken);
        assertThat(result.code()).isZero();
        Map<String, Object> returned = asMap(result.data());
        assertThat(longOf(returned, "overdueDays")).isZero();
        assertThat(longOf(returned, "fineCents")).isZero();
        assertThat(returned.get("returnedAt")).isNotNull();
        assertThat(availableCopies(bookId)).isEqualTo(2);
    }

    @Test
    void outOfStockRejected() {
        long bookId = createBook("978-7-121-00011-2", "只有一本", 1, 1000);
        long reader1 = createReader("借书人乙", "13900000002");
        long reader2 = createReader("借书人丙", "13900000003");
        borrow(bookId, reader1);

        ApiResult result = post("/api/loans", Map.of("bookId", bookId, "readerId", reader2), adminToken);
        assertThat(result.code()).isEqualTo(40906);
    }

    @Test
    void overdueFineRoundsUpPerDay() {
        long bookId = createBook("978-7-121-00012-3", "逾期取整书", 5, 10000);
        long readerId = createReader("借书人丁", "13900000004");

        // 逾期 30 分钟 → 不足一天计一天 → 1 天 × 50 分 = 50 分
        long loan1 = borrow(bookId, readerId);
        setDueAt(loan1, LocalDateTime.now().minusMinutes(30));
        ApiResult r1 = post("/api/loans/" + loan1 + "/return", null, adminToken);
        assertThat(r1.code()).isZero();
        assertThat(longOf(asMap(r1.data()), "overdueDays")).isEqualTo(1);
        assertThat(longOf(asMap(r1.data()), "fineCents")).isEqualTo(50);

        // 逾期 25 小时 → 向上取整 2 天 → 100 分
        // （第二版起逾期归还自动冻结读者，故第二次借书换新读者，罚款金额与读者无关）
        long reader2 = createReader("借书人丁二", "13900000014");
        long loan2 = borrow(bookId, reader2);
        setDueAt(loan2, LocalDateTime.now().minusHours(25));
        ApiResult r2 = post("/api/loans/" + loan2 + "/return", null, adminToken);
        assertThat(longOf(asMap(r2.data()), "overdueDays")).isEqualTo(2);
        assertThat(longOf(asMap(r2.data()), "fineCents")).isEqualTo(100);
    }

    @Test
    void fineCappedAtBookPrice() {
        long bookId = createBook("978-7-121-00013-4", "廉价书", 1, 100);
        long readerId = createReader("借书人戊", "13900000005");

        long loanId = borrow(bookId, readerId);
        setDueAt(loanId, LocalDateTime.now().minusHours(100)); // ceil = 5 天 = 250 分 > 书价 100 分

        ApiResult result = post("/api/loans/" + loanId + "/return", null, adminToken);
        assertThat(result.code()).isZero();
        assertThat(longOf(asMap(result.data()), "fineCents")).isEqualTo(100);
    }

    @Test
    void doubleReturnRejected() {
        long bookId = createBook("978-7-121-00014-5", "重复归还书", 1, 1000);
        long readerId = createReader("借书人己", "13900000006");
        long loanId = borrow(bookId, readerId);

        assertThat(post("/api/loans/" + loanId + "/return", null, adminToken).code()).isZero();
        ApiResult again = post("/api/loans/" + loanId + "/return", null, adminToken);
        assertThat(again.code()).isEqualTo(40907);
    }

    @Test
    void listLoansByOverdueAndStatus() {
        long bookId = createBook("978-7-121-00015-6", "查询用书", 5, 1000);
        long readerId = createReader("借书人庚", "13900000007");

        long loan1 = borrow(bookId, readerId);
        long loan2 = borrow(bookId, readerId);
        setDueAt(loan2, LocalDateTime.now().minusHours(3));

        ApiResult overdue = get("/api/loans?readerId={r}&overdue=true", adminToken, readerId);
        assertThat(overdue.code()).isZero();
        List<Map<String, Object>> overdueList = asList(overdue.data());
        assertThat(overdueList).hasSize(1);
        assertThat(longOf(overdueList.getFirst(), "id")).isEqualTo(loan2);
        assertThat(overdueList.getFirst().get("overdue")).isEqualTo(true);

        ApiResult borrowed = get("/api/loans?readerId={r}&status=BORROWED", adminToken, readerId);
        assertThat(asList(borrowed.data())).hasSize(2);

        post("/api/loans/" + loan1 + "/return", null, adminToken);
        ApiResult returned = get("/api/loans?readerId={r}&status=RETURNED", adminToken, readerId);
        assertThat(asList(returned.data())).hasSize(1);
    }

    @Test
    void concurrentBorrowLastCopy() throws Exception {
        long bookId = createBook("978-7-121-00016-7", "并发抢书", 1, 500);
        int threads = 10;
        List<Long> readerIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            readerIds.add(createReader("并发读者" + i, "1391000000" + String.format("%02d", i)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApiResult>> futures = new ArrayList<>();
        for (Long readerId : readerIds) {
            futures.add(pool.submit(() -> {
                start.await();
                return post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken);
            }));
        }
        start.countDown();

        int success = 0;
        int outOfStock = 0;
        for (Future<ApiResult> future : futures) {
            ApiResult result = future.get();
            if (result.code() == 0) {
                success++;
            } else if (result.code() == 40906) {
                outOfStock++;
            }
        }
        pool.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(outOfStock).isEqualTo(threads - 1);
        assertThat(availableCopies(bookId)).isZero();
    }
}

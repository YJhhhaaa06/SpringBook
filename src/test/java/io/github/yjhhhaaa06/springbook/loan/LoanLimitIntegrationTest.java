package io.github.yjhhhaaa06.springbook.loan;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 借阅上限集成测试（第二版）：默认上限 5，借满拒借、并发不超限、归还释放额度、履约借出同受限。
 */
class LoanLimitIntegrationTest extends IntegrationTestBase {

    @Test
    void sixthBorrowRejectedAtLimit() {
        long bookId = createBook("978-7-142-31001-2", "上限测试书", 6, 1000);
        long readerId = createReader("限额读者甲", "13860000001");

        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken).code())
                    .as("第 %d 本应成功", i + 1).isZero();
        }
        ApiResult sixth = post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken);
        assertThat(sixth.code()).isEqualTo(40913);
        assertThat(availableCopies(bookId)).isEqualTo(1);
    }

    @Test
    void concurrentBorrowRespectsLimit() throws Exception {
        long bookId = createBook("978-7-142-31002-1", "并发上限书", 8, 1000);
        long readerId = createReader("限额读者乙", "13860000002");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApiResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken);
            }));
        }
        start.countDown();

        int success = 0;
        int limitRejected = 0;
        for (Future<ApiResult> future : futures) {
            ApiResult result = future.get();
            if (result.code() == 0) {
                success++;
            } else if (result.code() == 40913) {
                limitRejected++;
            }
        }
        pool.shutdown();

        assertThat(success).isEqualTo(5);
        assertThat(limitRejected).isEqualTo(threads - 5);
        assertThat(availableCopies(bookId)).isEqualTo(3);
    }

    @Test
    void returnFreesQuota() {
        long bookId = createBook("978-7-142-31003-0", "额度释放书", 6, 1000);
        long readerId = createReader("限额读者丙", "13860000003");

        List<Long> loanIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            loanIds.add(borrow(bookId, readerId));
        }
        assertThat(post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken).code())
                .isEqualTo(40913);

        assertThat(post("/api/loans/" + loanIds.getFirst() + "/return", null, adminToken).code()).isZero();
        assertThat(post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken).code()).isZero();
    }

    @Test
    void fulfilmentBorrowAlsoBlockedAtLimit() {
        // A 借走唯一一本稀缺书，R 预约；R 借满 5 本；A 归还 → R 变 READY；R 履约借出 → 撞上限
        long scarce = createBook("978-7-142-31004-9", "履约上限稀缺书", 1, 1000);
        long filler = createBook("978-7-142-31005-8", "履约上限填充书", 5, 1000);
        long readerX = createReader("占书人乙", "13860000004");
        long readerR = createReader("预约限额人", "13860000005");

        long loanX = borrow(scarce, readerX);
        ApiResult reserve = post("/api/reservations", Map.of("bookId", scarce, "readerId", readerR), adminToken);
        assertThat(reserve.code()).isZero();

        for (int i = 0; i < 5; i++) {
            assertThat(borrow(filler, readerR)).isPositive();
        }

        assertThat(post("/api/loans/" + loanX + "/return", null, adminToken).code()).isZero();

        ApiResult fulfil = post("/api/loans", Map.of("bookId", scarce, "readerId", readerR), adminToken);
        assertThat(fulfil.code()).isEqualTo(40913);

        // 履约被拒后预约保持 READY，直到保留期过期顺延（惰性机制）
        ApiResult queue = get("/api/books/" + scarce + "/reservations", adminToken);
        assertThat(asList(queue.data()).getFirst().get("status")).isEqualTo("READY");
    }
}

package io.github.yjhhhaaa06.springbook.book;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入库/下架与读者管理集成测试（第二版）：
 * 总册数调整联动可借数、下架不得低于在借+预留、读者分页/修改/手动冻结解冻。
 */
class StockManagementIntegrationTest extends IntegrationTestBase {

    @Test
    void adjustCopiesUpAndDown() {
        long bookId = createBook("978-7-145-34001-4", "调册书", 2, 1000);

        ApiResult up = patch("/api/books/{id}/copies", Map.of("totalCopies", 5), adminToken, bookId);
        assertThat(up.code()).isZero();
        assertThat(availableCopies(bookId)).isEqualTo(5);

        ApiResult down = patch("/api/books/{id}/copies", Map.of("totalCopies", 3), adminToken, bookId);
        assertThat(down.code()).isZero();
        assertThat(availableCopies(bookId)).isEqualTo(3);
    }

    @Test
    void adjustCopiesBlockedBelowBorrowedAndReserved() {
        long bookId = createBook("978-7-145-34002-3", "下架保护书", 2, 1000);
        long holder = createReader("下架占用人", "13890000001");
        long reserver = createReader("下架预约人", "13890000002");

        // 借走 1 本 → 在借 1；总册数降到 1（在借 1 + 预留 0）允许，可借数归零
        long loan = borrow(bookId, holder);
        ApiResult toOne = patch("/api/books/{id}/copies", Map.of("totalCopies", 1), adminToken, bookId);
        assertThat(toOne.code()).isZero();
        assertThat(availableCopies(bookId)).isZero();

        // 库存为 0，reserver 预约成功（WAITING，不占名额）
        ApiResult reserve = post("/api/reservations", Map.of("bookId", bookId, "readerId", reserver), adminToken);
        assertThat(reserve.code()).isZero();
        long reservationId = longOf(asMap(reserve.data()), "id");

        // holder 归还 → 队首 reserver 变 READY 占 1 个名额：在借 0 + READY 预留 1 = 占用 1
        assertThat(post("/api/loans/" + loan + "/return", null, adminToken).code()).isZero();

        // 总册数降到 0 被拒（低于占用 1）；降到 1 允许
        ApiResult toZero = patch("/api/books/{id}/copies", Map.of("totalCopies", 0), adminToken, bookId);
        assertThat(toZero.code()).isEqualTo(40916);
        ApiResult toOneAgain = patch("/api/books/{id}/copies", Map.of("totalCopies", 1), adminToken, bookId);
        assertThat(toOneAgain.code()).isZero();

        // 取消 READY 释放预留（队列空，名额回架）→ 总册数可降到 0
        assertThat(delete("/api/reservations/" + reservationId, adminToken).code()).isZero();
        assertThat(patch("/api/books/{id}/copies", Map.of("totalCopies", 0), adminToken, bookId).code()).isZero();
    }

    @Test
    void adjustCopiesNotFound() {
        ApiResult result = patch("/api/books/{id}/copies", Map.of("totalCopies", 5), adminToken, 99999);
        assertThat(result.code()).isEqualTo(40401);
    }

    @Test
    void readerListPaginatedWithKeyword() {
        createReader("检索张三", "13890000003");
        createReader("检索李四", "13890000004");
        createReader("无关王五", "13890000005");

        ApiResult byKeyword = get("/api/readers?keyword={kw}", adminToken, "检索");
        assertThat(byKeyword.code()).isZero();
        Map<String, Object> page = asMap(byKeyword.data());
        assertThat(longOf(page, "total")).isGreaterThanOrEqualTo(2);

        ApiResult paged = get("/api/readers?page={p}&size={s}", adminToken, 1, 2);
        assertThat(paged.code()).isZero();
        Map<String, Object> page2 = asMap(paged.data());
        assertThat(longOf(page2, "page")).isEqualTo(1);
        assertThat(longOf(page2, "size")).isEqualTo(2);
        assertThat(asList(page2.get("records"))).hasSize(2);
    }

    @Test
    void updateReaderPhoneConflictAndSuccess() {
        long a = createReader("读者改甲", "13890000006");
        long b = createReader("读者改乙", "13890000007");

        ApiResult conflict = put("/api/readers/{id}",
                Map.of("name", "读者改乙", "phone", "13890000006"), adminToken, b);
        assertThat(conflict.code()).isEqualTo(40904);

        ApiResult ok = put("/api/readers/{id}",
                Map.of("name", "读者改乙新名", "phone", "13890000008"), adminToken, b);
        assertThat(ok.code()).isZero();
        assertThat(asMap(ok.data()).get("phone")).isEqualTo("13890000008");
    }

    @Test
    void manualFreezeBlocksBorrowAndUnfreezeRestores() {
        long bookId = createBook("978-7-145-34003-2", "手动冻结书", 2, 1000);
        long readerId = createReader("手动冻结读者", "13890000009");

        ApiResult freeze = put("/api/readers/{id}/status", Map.of("status", "FROZEN"), adminToken, readerId);
        assertThat(freeze.code()).isZero();
        assertThat(asMap(freeze.data()).get("status")).isEqualTo("FROZEN");

        ApiResult borrow = post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken);
        assertThat(borrow.code()).isEqualTo(40905);

        ApiResult unfreeze = put("/api/readers/{id}/status", Map.of("status", "ACTIVE"), adminToken, readerId);
        assertThat(unfreeze.code()).isZero();
        assertThat(post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken).code()).isZero();
    }

    @Test
    void readerStatusInvalidValueRejected() {
        long readerId = createReader("非法状态读者", "13890000010");
        ApiResult result = put("/api/readers/{id}/status", Map.of("status", "DELETED"), adminToken, readerId);
        assertThat(result.code()).isEqualTo(40001);
    }
}

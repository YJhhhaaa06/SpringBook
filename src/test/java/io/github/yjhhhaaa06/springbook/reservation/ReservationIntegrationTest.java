package io.github.yjhhhaaa06.springbook.reservation;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预约状态机集成测试：可借拒约、FIFO、归还触发 READY、履约借出、过期顺延、取消释放、删除保护。
 */
class ReservationIntegrationTest extends IntegrationTestBase {

    /** 场景工具：total=1 的书被 A 借走，B、C 依次预约并归还，返回各 id */
    private record Scene(long bookId, long readerA, long readerB, long readerC, long resB, long resC, long loanA) {
    }

    private Scene scene(String tag) {
        long bookId = createBook("978-7-131-" + tag + "-X", "预约场景" + tag, 1, 2000);
        long readerA = createReader("读者A" + tag, "1370000" + tag + "1");
        long readerB = createReader("读者B" + tag, "1370000" + tag + "2");
        long readerC = createReader("读者C" + tag, "1370000" + tag + "3");
        long loanA = borrow(bookId, readerA);

        ApiResult rB = post("/api/reservations", Map.of("bookId", bookId, "readerId", readerB), adminToken);
        ApiResult rC = post("/api/reservations", Map.of("bookId", bookId, "readerId", readerC), adminToken);
        assertThat(rB.code()).isZero();
        assertThat(rC.code()).isZero();
        long resB = longOf(asMap(rB.data()), "id");
        long resC = longOf(asMap(rC.data()), "id");
        return new Scene(bookId, readerA, readerB, readerC, resB, resC, loanA);
    }

    @Test
    void reserveNotAllowedWhileInStock() {
        long bookId = createBook("978-7-131-10001-1", "在架可借书", 2, 1000);
        long readerId = createReader("多管闲事", "13700011001");

        ApiResult result = post("/api/reservations", Map.of("bookId", bookId, "readerId", readerId), adminToken);
        assertThat(result.code()).isEqualTo(40908);
    }

    @Test
    void duplicateActiveReservationRejected() {
        Scene scene = scene("20001");
        ApiResult again = post("/api/reservations",
                Map.of("bookId", scene.bookId(), "readerId", scene.readerB()), adminToken);
        assertThat(again.code()).isEqualTo(40909);
    }

    @Test
    void returnMakesQueueHeadReadyAndHoldsCopy() {
        Scene scene = scene("20002");

        ApiResult returned = post("/api/loans/" + scene.loanA() + "/return", null, adminToken);
        assertThat(returned.code()).isZero();

        // 队首 B 变 READY，C 仍 WAITING；书被 B 的预留占位，可借数量保持 0
        ApiResult queue = get("/api/books/" + scene.bookId() + "/reservations", adminToken);
        assertThat(queue.code()).isZero();
        List<Map<String, Object>> items = asList(queue.data());
        assertThat(items.get(0).get("status")).isEqualTo("READY");
        assertThat(items.get(0).get("readyAt")).isNotNull();
        assertThat(items.get(0).get("holdUntil")).isNotNull();
        assertThat(items.get(1).get("status")).isEqualTo("WAITING");
        assertThat(availableCopies(scene.bookId())).isZero();
    }

    @Test
    void readyReaderBorrowsFulfilledWithoutStockDeduction() {
        Scene scene = scene("20003");
        post("/api/loans/" + scene.loanA() + "/return", null, adminToken);

        // B 在保留期内借书 → 履约，预约变 FULFILLED，库存保持 0（名额本就为其预留）
        ApiResult borrow = post("/api/loans",
                Map.of("bookId", scene.bookId(), "readerId", scene.readerB()), adminToken);
        assertThat(borrow.code()).isZero();

        ApiResult queue = get("/api/books/" + scene.bookId() + "/reservations", adminToken);
        List<Map<String, Object>> items = asList(queue.data());
        assertThat(items.get(0).get("status")).isEqualTo("FULFILLED");
        assertThat(items.get(1).get("status")).isEqualTo("WAITING");
        assertThat(availableCopies(scene.bookId())).isZero();
    }

    @Test
    void readyHoldsCopyFromOtherReaders() {
        Scene scene = scene("20004");
        post("/api/loans/" + scene.loanA() + "/return", null, adminToken);

        long readerD = createReader("路人丁", "13700020004");
        ApiResult other = post("/api/loans",
                Map.of("bookId", scene.bookId(), "readerId", readerD), adminToken);
        assertThat(other.code()).isEqualTo(40906);
    }

    @Test
    void expiredReadyHandsOverToNext() {
        Scene scene = scene("20005");
        post("/api/loans/" + scene.loanA() + "/return", null, adminToken);

        // B 的保留期改到过去，触发惰性清理（查询队列时）
        jdbc.update("UPDATE reservations SET hold_until = DATE_SUB(NOW(3), INTERVAL 1 HOUR) WHERE id = ?",
                scene.resB());

        ApiResult queue = get("/api/books/" + scene.bookId() + "/reservations", adminToken);
        List<Map<String, Object>> items = asList(queue.data());
        assertThat(items.get(0).get("status")).isEqualTo("EXPIRED");
        assertThat(items.get(1).get("status")).isEqualTo("READY");
        // 名额顺延给 C，可借数量仍为 0
        assertThat(availableCopies(scene.bookId())).isZero();

        // 队列清空后释放库存：C 取消后可借数量回 1
        ApiResult cancel = delete("/api/reservations/" + scene.resC(), adminToken);
        assertThat(cancel.code()).isZero();
        assertThat(availableCopies(scene.bookId())).isEqualTo(1);
    }

    @Test
    void cancelWaitingAndCancelReadyReleasesStock() {
        Scene scene = scene("20006");

        // WAITING 直接取消，无库存影响
        ApiResult cancelC = delete("/api/reservations/" + scene.resC(), adminToken);
        assertThat(cancelC.code()).isZero();

        // A 还书 → B READY（队列已空，无顺延对象）
        post("/api/loans/" + scene.loanA() + "/return", null, adminToken);
        assertThat(availableCopies(scene.bookId())).isZero();

        // B 取消 READY → 队列已空，名额释放回架
        ApiResult cancelB = delete("/api/reservations/" + scene.resB(), adminToken);
        assertThat(cancelB.code()).isZero();
        assertThat(availableCopies(scene.bookId())).isEqualTo(1);
    }

    @Test
    void deleteBlockedWithActiveReservation() {
        Scene scene = scene("20007");
        // 先归还：排除「未归还借阅」占用，验证「活跃预约」独立拦截删除
        ApiResult returned = post("/api/loans/" + scene.loanA() + "/return", null, adminToken);
        assertThat(returned.code()).isZero();
        ApiResult result = delete("/api/books/" + scene.bookId(), adminToken);
        assertThat(result.code()).isEqualTo(40903);
    }
}

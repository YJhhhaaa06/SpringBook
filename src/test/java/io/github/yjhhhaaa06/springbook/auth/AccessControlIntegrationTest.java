package io.github.yjhhhaaa06.springbook.auth;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据归属集成测试（第二版）：READER 只能操作自身资源（40300），ADMIN 可代办。
 */
class AccessControlIntegrationTest extends IntegrationTestBase {

    @Test
    void readerCannotBorrowForOthers() {
        long bookId = createBook("978-7-143-32001-6", "越权借书", 3, 1000);
        IntegrationTestBase.ReaderAccount me = registerReader("acc_borrow_me", "pass-123456", "本人甲", "13870000001");
        long other = createReader("他人甲", "13870000002");

        ApiResult forOther = post("/api/loans",
                Map.of("bookId", bookId, "readerId", other), me.token());
        assertThat(forOther.code()).isEqualTo(40300);

        ApiResult forSelf = post("/api/loans",
                Map.of("bookId", bookId, "readerId", me.readerId()), me.token());
        assertThat(forSelf.code()).isZero();
    }

    @Test
    void readerCannotReturnOthersLoan() {
        long bookId = createBook("978-7-143-32002-5", "越权还书", 2, 1000);
        IntegrationTestBase.ReaderAccount me = registerReader("acc_return_me", "pass-123456", "本人乙", "13870000003");
        long other = createReader("他人乙", "13870000004");

        long otherLoan = borrow(bookId, other);
        ApiResult result = post("/api/loans/" + otherLoan + "/return", null, me.token());
        assertThat(result.code()).isEqualTo(40300);

        // ADMIN 可正常归还（代办）
        assertThat(post("/api/loans/" + otherLoan + "/return", null, adminToken).code()).isZero();
    }

    @Test
    void readerLoanListForcedToSelf() {
        long bookId = createBook("978-7-143-32003-4", "越权查记录", 5, 1000);
        IntegrationTestBase.ReaderAccount me = registerReader("acc_list_me", "pass-123456", "本人丙", "13870000005");
        long other = createReader("他人丙", "13870000006");

        long myLoan = borrow(bookId, me.readerId());
        long otherLoan = borrow(bookId, other);

        // 不传 readerId → 只看自己
        ApiResult all = get("/api/loans", me.token());
        List<Map<String, Object>> items = asList(all.data());
        assertThat(items).hasSize(1);
        assertThat(longOf(items.getFirst(), "id")).isEqualTo(myLoan);

        // 显式传他人 readerId → 403
        ApiResult peekOther = get("/api/loans?readerId={r}", me.token(), other);
        assertThat(peekOther.code()).isEqualTo(40300);

        // 显式传自己的 readerId → 合法
        assertThat(get("/api/loans?readerId={r}", me.token(), me.readerId()).code()).isZero();
    }

    @Test
    void readerCannotViewOtherReaderDetail() {
        IntegrationTestBase.ReaderAccount me = registerReader("acc_detail_me", "pass-123456", "本人丁", "13870000007");
        long other = createReader("他人丁", "13870000008");

        ApiResult self = get("/api/readers/{id}", me.token(), me.readerId());
        assertThat(self.code()).isZero();

        ApiResult peek = get("/api/readers/{id}", me.token(), other);
        assertThat(peek.code()).isEqualTo(40300);
    }

    @Test
    void readerCannotReserveOrCancelForOthers() {
        long bookId = createBook("978-7-143-32004-3", "越权预约书", 1, 1000);
        IntegrationTestBase.ReaderAccount me = registerReader("acc_resv_me", "pass-123456", "本人戊", "13870000009");
        long other = createReader("他人戊", "13870000010");

        long holderLoan = borrow(bookId, other);

        ApiResult forOther = post("/api/reservations",
                Map.of("bookId", bookId, "readerId", other), me.token());
        assertThat(forOther.code()).isEqualTo(40300);

        ApiResult forSelf = post("/api/reservations",
                Map.of("bookId", bookId, "readerId", me.readerId()), me.token());
        assertThat(forSelf.code()).isZero();
        long myReservation = longOf(asMap(forSelf.data()), "id");

        // ADMIN 再替 other 排一条队，me 尝试取消他人的预约
        ApiResult adminForOther = post("/api/reservations",
                Map.of("bookId", bookId, "readerId", other), adminToken);
        assertThat(adminForOther.code()).isZero();
        long otherReservation = longOf(asMap(adminForOther.data()), "id");

        ApiResult cancelOther = delete("/api/reservations/" + otherReservation, me.token());
        assertThat(cancelOther.code()).isEqualTo(40300);

        ApiResult cancelMine = delete("/api/reservations/" + myReservation, me.token());
        assertThat(cancelMine.code()).isZero();
    }

    @Test
    void readerCannotPayFineOrManageReaders() {
        long bookId = createBook("978-7-143-32005-2", "越权管理书", 2, 1000);
        IntegrationTestBase.ReaderAccount me = registerReader("acc_admin_me", "pass-123456", "本人己", "13870000011");

        long loanId = borrow(bookId, me.readerId());

        assertThat(post("/api/loans/" + loanId + "/fine/pay", null, me.token()).code()).isEqualTo(40300);
        assertThat(get("/api/readers", me.token()).code()).isEqualTo(40300);
        assertThat(put("/api/readers/{id}", Map.of("name", "改名", "phone", "13870000012"), me.token(), me.readerId())
                .code()).isEqualTo(40300);
        assertThat(put("/api/readers/{id}/status", Map.of("status", "FROZEN"), me.token(), me.readerId())
                .code()).isEqualTo(40300);
        assertThat(patch("/api/books/{id}/copies", Map.of("totalCopies", 5), me.token(), bookId)
                .code()).isEqualTo(40300);
    }
}

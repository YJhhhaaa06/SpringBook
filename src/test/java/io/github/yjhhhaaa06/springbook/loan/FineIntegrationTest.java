package io.github.yjhhhaaa06.springbook.loan;

import io.github.yjhhhaaa06.springbook.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 罚款闭环集成测试（第二版）：逾期归还 → UNPAID → 禁借/禁预约 → 缴纳 → 自动解冻 → 可再借。
 * 自动冻结在归还时发生，自动解冻要求无待缴罚款且无逾期在借。
 */
class FineIntegrationTest extends IntegrationTestBase {

    private long unpaidLoan(long bookId, long readerId, long overdueHours) {
        long loanId = borrow(bookId, readerId);
        setDueAt(loanId, LocalDateTime.now().minusHours(overdueHours));
        ApiResult returned = post("/api/loans/" + loanId + "/return", null, adminToken);
        assertThat(returned.code()).isZero();
        return loanId;
    }

    @Test
    void overdueReturnMarksUnpaidAndBlocksBorrowAndReserve() {
        long bookId = createBook("978-7-141-30001-9", "罚款闭环书", 3, 5000);
        long scarce = createBook("978-7-141-30002-8", "罚款闭环稀缺书", 1, 1000);
        long readerId = createReader("欠款人甲", "13850000001");

        long loanId = unpaidLoan(bookId, readerId, 10);
        assertThat(readerStatus(readerId)).isEqualTo("FROZEN");

        // 归还响应不直接带 fineStatus，查记录确认 UNPAID（逾期 10 小时 → 1 天 × 50 分）
        ApiResult loans = get("/api/loans?readerId={r}", adminToken, readerId);
        List<Map<String, Object>> items = asList(loans.data());
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().get("fineStatus")).isEqualTo("UNPAID");
        assertThat(longOf(items.getFirst(), "fineCents")).isEqualTo(50);

        // 未缴清禁止借书：此时读者已冻结，FROZEN 检查（40905）先于罚款检查；
        // 「未缴清且未冻结 → 40914」由 manualUnfreezeDoesNotLendWhileFineUnpaid 独立覆盖
        ApiResult borrow = post("/api/loans", Map.of("bookId", scarce, "readerId", readerId), adminToken);
        assertThat(borrow.code()).isEqualTo(40905);

        // 未缴清禁止预约（另一本书先被借空）
        long holder = createReader("占书人甲", "13850000002");
        borrow(scarce, holder);
        ApiResult reserve = post("/api/reservations", Map.of("bookId", scarce, "readerId", readerId), adminToken);
        assertThat(reserve.code()).isEqualTo(40914);

        // 缴纳 → PAID → 自动解冻 → 可再借
        ApiResult pay = post("/api/loans/" + loanId + "/fine/pay", null, adminToken);
        assertThat(pay.code()).isZero();
        assertThat(asMap(pay.data()).get("fineStatus")).isEqualTo("PAID");
        assertThat(readerStatus(readerId)).isEqualTo("ACTIVE");

        assertThat(post("/api/loans", Map.of("bookId", bookId, "readerId", readerId), adminToken).code()).isZero();
    }

    @Test
    void payFineAutoUnfreezesOnlyWhenNoOverdueBorrowed() {
        long book1 = createBook("978-7-141-30003-7", "解冻判定书一", 2, 2000);
        long book2 = createBook("978-7-141-30004-6", "解冻判定书二", 2, 2000);
        long readerId = createReader("欠款人乙", "13850000003");

        long loan1 = borrow(book1, readerId);
        long loan2 = borrow(book2, readerId);
        setDueAt(loan1, LocalDateTime.now().minusHours(5));
        setDueAt(loan2, LocalDateTime.now().minusHours(5));

        // book1 逾期归还 → 冻结 + 待缴；book2 仍逾期在借
        assertThat(post("/api/loans/" + loan1 + "/return", null, adminToken).code()).isZero();
        assertThat(readerStatus(readerId)).isEqualTo("FROZEN");

        // 缴清 book1 罚款，但 book2 逾期在借 → 不解冻
        assertThat(post("/api/loans/" + loan1 + "/fine/pay", null, adminToken).code()).isZero();
        assertThat(readerStatus(readerId)).isEqualTo("FROZEN");

        // book2 逾期归还（又产生待缴）→ 缴清 → 无待缴无逾期在借 → 解冻
        assertThat(post("/api/loans/" + loan2 + "/return", null, adminToken).code()).isZero();
        assertThat(post("/api/loans/" + loan2 + "/fine/pay", null, adminToken).code()).isZero();
        assertThat(readerStatus(readerId)).isEqualTo("ACTIVE");
    }

    @Test
    void payFineTwiceRejected() {
        long bookId = createBook("978-7-141-30005-5", "重复缴纳书", 2, 1000);
        long readerId = createReader("欠款人丙", "13850000004");
        long loanId = unpaidLoan(bookId, readerId, 8);

        assertThat(post("/api/loans/" + loanId + "/fine/pay", null, adminToken).code()).isZero();
        assertThat(post("/api/loans/" + loanId + "/fine/pay", null, adminToken).code()).isEqualTo(40917);
    }

    @Test
    void overdueReturnAutoFreezesAndBlocksBorrow() {
        long bookId = createBook("978-7-141-30006-4", "自动冻结书", 2, 1000);
        long readerId = createReader("逾期人丁", "13850000005");

        long loanId = borrow(bookId, readerId);
        setDueAt(loanId, LocalDateTime.now().minusHours(3));
        assertThat(post("/api/loans/" + loanId + "/return", null, adminToken).code()).isZero();
        assertThat(readerStatus(readerId)).isEqualTo("FROZEN");

        // 冻结读者借书被拒：FROZEN 检查（40905）先于罚款检查（40914）
        long other = createBook("978-7-141-30007-3", "冻结拦截书", 2, 1000);
        ApiResult borrow = post("/api/loans", Map.of("bookId", other, "readerId", readerId), adminToken);
        assertThat(borrow.code()).isEqualTo(40905);
    }

    @Test
    void manualUnfreezeDoesNotLendWhileFineUnpaid() {
        long bookId = createBook("978-7-141-30008-2", "手动解冻书", 2, 1000);
        long readerId = createReader("欠款人戊", "13850000006");
        unpaidLoan(bookId, readerId, 6);

        // 管理员手动解冻绕不过罚款禁借（禁借依据是 UNPAID 记录而非读者状态）
        ApiResult unfreeze = put("/api/readers/{id}/status", Map.of("status", "ACTIVE"), adminToken, readerId);
        assertThat(unfreeze.code()).isZero();
        assertThat(readerStatus(readerId)).isEqualTo("ACTIVE");

        long other = createBook("978-7-141-30009-1", "罚款优先书", 2, 1000);
        ApiResult borrow = post("/api/loans", Map.of("bookId", other, "readerId", readerId), adminToken);
        assertThat(borrow.code()).isEqualTo(40914);
    }
}

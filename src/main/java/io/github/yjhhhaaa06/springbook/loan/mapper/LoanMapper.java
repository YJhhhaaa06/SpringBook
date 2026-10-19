package io.github.yjhhhaaa06.springbook.loan.mapper;

import io.github.yjhhhaaa06.springbook.loan.domain.Loan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅记录表访问。XML 实现见 classpath:mapper/LoanMapper.xml。
 */
@Mapper
public interface LoanMapper {

    int insert(Loan loan);

    /** join books/readers 带出 bookTitle / readerName */
    Loan findById(@Param("id") Long id);

    /** 结束借阅：仅当 status = BORROWED 时生效，返回受影响行数 */
    int markReturned(@Param("id") Long id,
                     @Param("returnedAt") LocalDateTime returnedAt,
                     @Param("fineCents") Integer fineCents);

    /** 该书未归还借阅数（删除图书前置校验） */
    long countBorrowedByBook(@Param("bookId") Long bookId);

    /** 某读者的在借列表（join books 带出书名），按应还时间升序 */
    List<Loan> listBorrowedByReader(@Param("readerId") Long readerId);

    /**
     * 借阅记录查询：overdue = true 时按「在借且已逾期」过滤（此时忽略 status），
     * 否则按可选 status 过滤。
     */
    List<Loan> listByQuery(@Param("readerId") Long readerId,
                           @Param("status") String status,
                           @Param("overdue") Boolean overdue,
                           @Param("now") LocalDateTime now);
}

package io.github.yjhhhaaa06.springbook.reservation.mapper;

import io.github.yjhhhaaa06.springbook.reservation.domain.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约表访问。XML 实现见 classpath:mapper/ReservationMapper.xml。
 */
@Mapper
public interface ReservationMapper {

    int insert(Reservation reservation);

    Reservation findById(@Param("id") Long id);

    /** 该书活跃（WAITING/READY）预约数，删除图书前置校验 */
    long countActiveByBook(@Param("bookId") Long bookId);

    /** 该书未过期 READY 预约数（占用了可借名额，下架校验用） */
    long countReadyByBook(@Param("bookId") Long bookId, @Param("now") LocalDateTime now);

    /** 同一读者对同一本书的活跃预约数（唯一活跃预约校验） */
    long countActiveByReaderAndBook(@Param("bookId") Long bookId, @Param("readerId") Long readerId);

    /** FIFO 取队首 WAITING */
    Reservation findFirstWaitingByBook(@Param("bookId") Long bookId);

    /** 该读者对该书未过期（hold_until &gt; now）的 READY 预约 */
    Reservation findReadyByBookAndReader(@Param("bookId") Long bookId,
                                         @Param("readerId") Long readerId,
                                         @Param("now") LocalDateTime now);

    /** 查出该书已超过保留期的 READY（随后逐个置 EXPIRED 并顺延） */
    List<Reservation> listOverdueReady(@Param("bookId") Long bookId, @Param("now") LocalDateTime now);

    /** 更新状态；readyAt/holdUntil 仅在目标状态为 READY 时有值 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("readyAt") LocalDateTime readyAt,
                     @Param("holdUntil") LocalDateTime holdUntil);

    /** 该书预约队列（含历史），按排队时间升序；join readers 带出姓名 */
    List<Reservation> listByBook(@Param("bookId") Long bookId);
}

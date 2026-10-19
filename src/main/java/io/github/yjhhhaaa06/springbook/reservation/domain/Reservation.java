package io.github.yjhhhaaa06.springbook.reservation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约（对应表 reservations）。
 * 状态机：WAITING → READY → FULFILLED，旁路 CANCELLED / EXPIRED。
 */
@Data
public class Reservation {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private Long id;
    private Long bookId;
    private Long readerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime readyAt;
    private LocalDateTime holdUntil;

    /** join 带出的展示字段 */
    private String readerName;
}

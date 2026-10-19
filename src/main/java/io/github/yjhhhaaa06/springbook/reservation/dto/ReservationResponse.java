package io.github.yjhhhaaa06.springbook.reservation.dto;

import io.github.yjhhhaaa06.springbook.reservation.domain.Reservation;

import java.time.LocalDateTime;

/**
 * 预约响应。
 */
public record ReservationResponse(
        Long id,
        Long bookId,
        Long readerId,
        String readerName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime readyAt,
        LocalDateTime holdUntil
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getBookId(),
                reservation.getReaderId(),
                reservation.getReaderName(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getReadyAt(),
                reservation.getHoldUntil()
        );
    }
}

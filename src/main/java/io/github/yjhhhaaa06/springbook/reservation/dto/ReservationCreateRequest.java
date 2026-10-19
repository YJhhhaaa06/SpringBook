package io.github.yjhhhaaa06.springbook.reservation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 预约请求。仅当图书可借数量为 0 时允许。
 */
public record ReservationCreateRequest(

        @NotNull(message = "bookId 不能为空")
        Long bookId,

        @NotNull(message = "readerId 不能为空")
        Long readerId
) {
}

package io.github.yjhhhaaa06.springbook.reservation.controller;

import io.github.yjhhhaaa06.springbook.auth.AuthPrincipal;
import io.github.yjhhhaaa06.springbook.common.Result;
import io.github.yjhhhaaa06.springbook.reservation.dto.ReservationCreateRequest;
import io.github.yjhhhaaa06.springbook.reservation.dto.ReservationResponse;
import io.github.yjhhhaaa06.springbook.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预约接口：发起预约、取消预约（数据归属见 AccessPolicy）、查看某书预约队列（均要求已认证）。
 */
@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/api/reservations")
    public Result<ReservationResponse> create(@Valid @RequestBody ReservationCreateRequest request,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(reservationService.create(request.bookId(), request.readerId(), principal));
    }

    @DeleteMapping("/api/reservations/{id}")
    public Result<ReservationResponse> cancel(@PathVariable Long id,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return Result.ok(reservationService.cancel(id, principal));
    }

    @GetMapping("/api/books/{bookId}/reservations")
    public Result<List<ReservationResponse>> listByBook(@PathVariable Long bookId) {
        return Result.ok(reservationService.listByBook(bookId));
    }
}

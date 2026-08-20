package com.namatdang.namatdang.reservation.controller;

import com.namatdang.namatdang.idempotency.service.IdempotentResponse;
import com.namatdang.namatdang.reservation.dto.ReservationCreateRequestDto;
import com.namatdang.namatdang.reservation.dto.ReservationDetailResponseDto;
import com.namatdang.namatdang.reservation.dto.ReservationPageResponseDto;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import com.namatdang.namatdang.reservation.service.ReservationService;
import com.namatdang.namatdang.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
@Tag(name = "예약", description = "소비자 예약 생성·조회·취소 API")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @Operation(summary = "예약 생성")
    public ResponseEntity<Object> createReservation(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationCreateRequestDto requestDto
    ) {
        IdempotentResponse response =
                reservationService.createReservation(authUser.getUserId(), idempotencyKey, requestDto);
        return response.toResponseEntity();
    }

    @PostMapping("/{reservationId}/cancel")
    @Operation(summary = "예약 취소")
    public ResponseEntity<Object> cancelReservation(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable Long reservationId
    ) {
        IdempotentResponse response =
                reservationService.cancelReservation(authUser.getUserId(), idempotencyKey, reservationId);
        return response.toResponseEntity();
    }

    @GetMapping
    @Operation(summary = "내 예약 목록 조회")
    public ResponseEntity<ReservationPageResponseDto> getMyReservations(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ReservationPageResponseDto responseDto =
                reservationService.getMyReservations(authUser.getUserId(), status, page, size);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "내 예약 상세 조회")
    public ResponseEntity<ReservationDetailResponseDto> getMyReservation(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long reservationId
    ) {
        ReservationDetailResponseDto responseDto =
                reservationService.getMyReservation(authUser.getUserId(), reservationId);
        return ResponseEntity.ok(responseDto);
    }
}

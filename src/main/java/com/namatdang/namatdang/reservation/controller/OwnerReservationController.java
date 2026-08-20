package com.namatdang.namatdang.reservation.controller;

import com.namatdang.namatdang.reservation.dto.ReservationDetailResponseDto;
import com.namatdang.namatdang.reservation.dto.ReservationPageResponseDto;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import com.namatdang.namatdang.reservation.service.OwnerReservationService;
import com.namatdang.namatdang.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner")
@Tag(name = "예약 관리", description = "사장님 예약 조회 및 수령 완료 API")
public class OwnerReservationController {

    private final OwnerReservationService ownerReservationService;

    /**
     * 수령 완료는 Idempotency-Key를 받지 않는다. 예약 행 잠금과 PICKED_UP 재호출 시
     * 현재 결과 반환으로 같은 효과를 보장한다.
     */
    @PostMapping("/reservations/{reservationId}/pickup")
    @Operation(summary = "예약 수령 완료 처리")
    public ResponseEntity<ReservationDetailResponseDto> pickUpReservation(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long reservationId
    ) {
        ReservationDetailResponseDto responseDto =
                ownerReservationService.pickUpReservation(authUser.getUserId(), reservationId);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/stores/{storeId}/reservations")
    @Operation(summary = "내 매장의 예약 목록 조회")
    public ResponseEntity<ReservationPageResponseDto> getStoreReservations(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long storeId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ReservationPageResponseDto responseDto =
                ownerReservationService.getStoreReservations(authUser.getUserId(), storeId, status, page, size);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(summary = "내 매장의 예약 상세 조회")
    public ResponseEntity<ReservationDetailResponseDto> getStoreReservation(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long reservationId
    ) {
        ReservationDetailResponseDto responseDto =
                ownerReservationService.getStoreReservation(authUser.getUserId(), reservationId);
        return ResponseEntity.ok(responseDto);
    }
}

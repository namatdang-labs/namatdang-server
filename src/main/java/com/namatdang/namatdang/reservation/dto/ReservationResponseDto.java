package com.namatdang.namatdang.reservation.dto;

import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 목록 응답. 상세와 같은 필드 구조를 쓰되 품목 배열만 제외한다.
 */
@Getter
@AllArgsConstructor
public class ReservationResponseDto {

    private Long reservationId;
    private Long dealId;
    private Long storeId;
    private String storeName;
    private ReservationStatus status;
    private long totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime canceledAt;
    private LocalDateTime pickedUpAt;

    public static ReservationResponseDto from(Reservation reservation) {
        return new ReservationResponseDto(reservation.getId(),
                                          reservation.getDeal().getId(),
                                          reservation.getDeal().getStore().getId(),
                                          reservation.getDeal().getStore().getName(),
                                          reservation.getStatus(),
                                          reservation.getTotalAmount(),
                                          reservation.getCreatedAt(),
                                          reservation.getCanceledAt(),
                                          reservation.getPickedUpAt());
    }
}

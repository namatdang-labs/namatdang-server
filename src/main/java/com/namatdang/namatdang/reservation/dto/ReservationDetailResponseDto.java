package com.namatdang.namatdang.reservation.dto;

import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.entity.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReservationDetailResponseDto {

    private Long reservationId;
    private Long dealId;
    private Long storeId;
    private String storeName;
    private ReservationStatus status;
    private long totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime canceledAt;
    private LocalDateTime pickedUpAt;
    private List<ReservationItemResponseDto> items;

    public static ReservationDetailResponseDto from(Reservation reservation) {
        List<ReservationItemResponseDto> items = reservation.getItems().stream()
                .map(ReservationItemResponseDto::from)
                .toList();

        return new ReservationDetailResponseDto(reservation.getId(),
                                                reservation.getDeal().getId(),
                                                reservation.getDeal().getStore().getId(),
                                                reservation.getDeal().getStore().getName(),
                                                reservation.getStatus(),
                                                reservation.getTotalAmount(),
                                                reservation.getCreatedAt(),
                                                reservation.getCanceledAt(),
                                                reservation.getPickedUpAt(),
                                                items);
    }
}

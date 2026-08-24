package com.namatdang.namatdang.reservation.dto;

import com.namatdang.namatdang.reservation.entity.ReservationItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReservationItemResponseDto {

    private Long dealItemId;
    private String name;
    private int salePrice;
    private int quantity;
    private long subtotal;

    /**
     * 원본 DealItem의 현재 값이 아니라 예약 당시 스냅샷을 그대로 반환한다(RSV-11).
     */
    public static ReservationItemResponseDto from(ReservationItem item) {
        return new ReservationItemResponseDto(item.getDealItem().getId(),
                                              item.getName(),
                                              item.getSalePrice(),
                                              item.getQuantity(),
                                              item.getSubtotal());
    }
}

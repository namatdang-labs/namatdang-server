package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.entity.DealItemStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DealItemResponseDto {

    private Long dealItemId;
    private String name;
    private int totalQuantity;
    private int remainingQuantity;
    private int originalPrice;
    private int salePrice;
    private int discountRate;
    private DealItemStatus status;

    public static DealItemResponseDto from(DealItem item) {
        return new DealItemResponseDto(item.getId(),
                                       item.getName(),
                                       item.getTotalQuantity(),
                                       item.getRemainingQuantity(),
                                       item.getOriginalPrice(),
                                       item.getSalePrice(),
                                       item.discountRate(),
                                       item.getStatus());
    }
}

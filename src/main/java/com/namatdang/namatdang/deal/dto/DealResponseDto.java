package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DealResponseDto {

    private Long dealId;
    private Long storeId;
    private String storeName;
    private LocalDateTime pickupDeadline;
    private DealStatus status;
    private String description;
    private int itemCount;
    private int lowestSalePrice;
    private LocalDateTime createdAt;

    public static DealResponseDto from(Deal deal) {
        return new DealResponseDto(deal.getId(),
                                   deal.getStore().getId(),
                                   deal.getStore().getName(),
                                   deal.getPickupDeadline(),
                                   deal.displayStatus(LocalDateTime.now()),
                                   deal.getDescription(),
                                   deal.getItems().size(),
                                   deal.lowestSalePrice(),
                                   deal.getCreatedAt());
    }
}

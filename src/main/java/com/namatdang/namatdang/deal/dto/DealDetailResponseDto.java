package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DealDetailResponseDto {

    private Long dealId;
    private Long storeId;
    private String storeName;
    private LocalDateTime pickupDeadline;
    private DealStatus status;
    private String description;
    private List<DealItemResponseDto> items;
    private LocalDateTime createdAt;

    public static DealDetailResponseDto from(Deal deal) {
        List<DealItemResponseDto> items = deal.getItems().stream()
                .map(DealItemResponseDto::from)
                .toList();

        return new DealDetailResponseDto(deal.getId(),
                                         deal.getStore().getId(),
                                         deal.getStore().getName(),
                                         deal.getPickupDeadline(),
                                         deal.displayStatus(LocalDateTime.now()),
                                         deal.getDescription(),
                                         items,
                                         deal.getCreatedAt());
    }
}

package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.deal.entity.DealItem;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.media.ImageUrls;
import com.namatdang.namatdang.media.ImageVariant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DealResponseDto {

    private Long dealId;
    private Long storeId;
    private String storeName;
    private LocalDateTime salesEndsAt;
    private DealStatus status;
    private String description;
    private String imageUrl;
    private int itemCount;
    private int lowestSalePrice;
    private Long distanceMeters;
    private String headlineItemName;
    private int totalRemainingQuantity;
    private int maxDiscountRate;
    private LocalDateTime createdAt;

    public static DealResponseDto from(Deal deal) {
        return from(deal, null, deal.getItems());
    }

    public static DealResponseDto from(Deal deal, Long distanceMeters) {
        return from(deal, distanceMeters, deal.getItems());
    }

    public static DealResponseDto fromSelling(Deal deal) {
        return fromSelling(deal, null);
    }

    public static DealResponseDto fromSelling(Deal deal, Long distanceMeters) {
        List<DealItem> availableItems = deal.getItems().stream()
                .filter(item -> item.getRemainingQuantity() > 0)
                .toList();

        return from(deal, distanceMeters, availableItems);
    }

    private static DealResponseDto from(Deal deal, Long distanceMeters,
                                        List<DealItem> summaryItems) {
        return new DealResponseDto(deal.getId(),
                                   deal.getStore().getId(),
                                   deal.getStore().getName(),
                                   deal.getSalesEndsAt(),
                                   deal.displayStatus(LocalDateTime.now()),
                                   deal.getDescription(),
                                   ImageUrls.forDeal(deal, ImageVariant.CARD),
                                   summaryItems.size(),
                                   summaryItems.stream()
                                           .mapToInt(DealItem::getSalePrice)
                                           .min()
                                           .orElse(0),
                                   distanceMeters,
                                   summaryItems.stream()
                                           .findFirst()
                                           .map(DealItem::getName)
                                           .orElse(null),
                                   summaryItems.stream()
                                           .mapToInt(DealItem::getRemainingQuantity)
                                           .sum(),
                                   summaryItems.stream()
                                           .mapToInt(DealItem::discountRate)
                                           .max()
                                           .orElse(0),
                                   deal.getCreatedAt());
    }
}

package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.media.ImageUrls;
import com.namatdang.namatdang.media.ImageVariant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RecentDealImageResponseDto {

    private Long dealId;
    private String imageUrl;
    private LocalDateTime createdAt;

    public static RecentDealImageResponseDto from(Deal deal) {
        return new RecentDealImageResponseDto(
                deal.getId(),
                ImageUrls.forDeal(deal, ImageVariant.DETAIL),
                deal.getCreatedAt()
        );
    }
}

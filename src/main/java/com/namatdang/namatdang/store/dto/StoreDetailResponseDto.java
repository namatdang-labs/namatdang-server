package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.media.ImageUrls;
import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.store.entity.Store;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreDetailResponseDto {

    private Long id;
    private String name;
    private String address;
    private String addressDetail;
    private String phoneNumber;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String imageUrl;
    private List<RecentDealImageResponseDto> recentDealImages;

    public static StoreDetailResponseDto of(Store store, List<Deal> recentImageDeals) {
        return new StoreDetailResponseDto(
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getAddressDetail(),
                store.getPhoneNumber(),
                store.getDescription(),
                store.getLatitude(),
                store.getLongitude(),
                ImageUrls.forStore(store, ImageVariant.DETAIL),
                recentImageDeals.stream()
                        .map(RecentDealImageResponseDto::from)
                        .toList()
        );
    }
}

package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.media.ImageUrls;
import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.store.entity.Store;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "지도 영역 매장 검색 응답 DTO")
public class StoreMapResponseDto {

    @Schema(description = "매장 ID", example = "1")
    private Long id;

    @Schema(description = "매장명", example = "북촌 유기농밀명인 단팥빵")
    private String name;

    @Schema(description = "도로명 주소", example = "서울특별시 종로구 계동길 5")
    private String address;

    @Schema(description = "상세 주소", example = "1층 105호")
    private String addressDetail;

    @Schema(description = "전화번호", example = "027411487")
    private String phoneNumber;

    @Schema(description = "위도", example = "37.5776238")
    private BigDecimal latitude;

    @Schema(description = "경도", example = "126.9865539")
    private BigDecimal longitude;

    @Schema(description = "현재 활성(할인 판매 중) 딜 보유 여부", example = "true")
    private boolean hasActiveDeal;

    @Schema(description = "현재 판매 중인 딜 개수", example = "1")
    private long activeDealCount;

    @Schema(description = "지도 팝업용 최신 딜 썸네일 URL(없으면 매장 대표 썸네일)", nullable = true)
    private String imageUrl;

    @Schema(description = "카드용 최신 딜 이미지 URL(없으면 매장 대표 이미지)", nullable = true)
    private String cardImageUrl;

    public static StoreMapResponseDto of(Store store, long activeDealCount, Deal latestImageDeal) {
        return StoreMapResponseDto.builder()
                .id(store.getId())
                .name(store.getName())
                .address(store.getAddress())
                .addressDetail(store.getAddressDetail())
                .phoneNumber(store.getPhoneNumber())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .hasActiveDeal(activeDealCount > 0)
                .activeDealCount(activeDealCount)
                .imageUrl(latestImageDeal == null
                        ? ImageUrls.forStore(store, ImageVariant.THUMBNAIL)
                        : ImageUrls.forDeal(latestImageDeal, ImageVariant.THUMBNAIL))
                .cardImageUrl(latestImageDeal == null
                        ? ImageUrls.forStore(store, ImageVariant.CARD)
                        : ImageUrls.forDeal(latestImageDeal, ImageVariant.CARD))
                .build();
    }
}

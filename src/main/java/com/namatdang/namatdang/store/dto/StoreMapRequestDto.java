package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "지도 영역 기반 매장 검색 요청 DTO")
public class StoreMapRequestDto {

    @NotNull(message = "최소 위도(minLat)는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도 범위를 확인해 주세요.")
    @DecimalMax(value = "90.0", message = "위도 범위를 확인해 주세요.")
    @Schema(description = "화면 남서쪽 최소 위도", example = "37.5600")
    private BigDecimal minLat;

    @NotNull(message = "최대 위도(maxLat)는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도 범위를 확인해 주세요.")
    @DecimalMax(value = "90.0", message = "위도 범위를 확인해 주세요.")
    @Schema(description = "화면 북동쪽 최대 위도", example = "37.5800")
    private BigDecimal maxLat;

    @NotNull(message = "최소 경도(minLng)는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도 범위를 확인해 주세요.")
    @DecimalMax(value = "180.0", message = "경도 범위를 확인해 주세요.")
    @Schema(description = "화면 남서쪽 최소 경도", example = "126.9700")
    private BigDecimal minLng;

    @NotNull(message = "최대 경도(maxLng)는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도 범위를 확인해 주세요.")
    @DecimalMax(value = "180.0", message = "경도 범위를 확인해 주세요.")
    @Schema(description = "화면 북동쪽 최대 경도", example = "127.0000")
    private BigDecimal maxLng;

    @Schema(description = "현재 할인 판매 중인 매장만 조회 여부 (기본: false)", example = "true")
    private Boolean onlyDiscounting = false;

    @Schema(description = "매장명 또는 주소 검색어", example = "단팥빵")
    private String keyword;

    @Min(value = 1, message = "조회 개수는 1개 이상이어야 합니다.")
    @Max(value = 100, message = "조회 개수는 100개 이하여야 합니다.")
    @Schema(description = "조회할 최대 매장 수 (기본: 50, 최대: 100)", example = "50", defaultValue = "50")
    private int limit = 50;

    public StoreMapRequestDto(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng,
                              Boolean onlyDiscounting, String keyword) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.onlyDiscounting = onlyDiscounting != null && onlyDiscounting;
        this.keyword = keyword;
    }

    public void validateBounds() {
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
        if (minLat.compareTo(maxLat) > 0 || minLng.compareTo(maxLng) > 0) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }
}

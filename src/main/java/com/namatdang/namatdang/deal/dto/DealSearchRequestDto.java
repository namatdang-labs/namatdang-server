package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "판매 중 딜 목록 검색 요청 DTO")
public class DealSearchRequestDto {

    public static final int DEFAULT_RADIUS_METERS = 5_000;
    public static final int MIN_RADIUS_METERS = 100;
    public static final int MAX_RADIUS_METERS = 50_000;
    public static final int MAX_PAGE_SIZE = 100;

    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    @Schema(description = "검색 중심 위도. centerLng와 함께 전달해야 합니다.", example = "35.8714354")
    private BigDecimal centerLat;

    @Schema(description = "검색 중심 경도. centerLat와 함께 전달해야 합니다.", example = "128.6014450")
    private BigDecimal centerLng;

    @Schema(description = "반경(미터)", example = "5000", defaultValue = "5000",
            minimum = "100", maximum = "50000")
    private int radiusMeters = DEFAULT_RADIUS_METERS;

    @Schema(description = "딜 설명, 가게명·주소 또는 품목명 검색어", example = "소금빵")
    private String keyword;

    @Schema(description = "페이지 번호", example = "0", defaultValue = "0", minimum = "0")
    private int page = 0;

    @Schema(description = "페이지 크기", example = "20", defaultValue = "20",
            minimum = "1", maximum = "100")
    private int size = 20;

    public boolean hasLocation() {
        return centerLat != null && centerLng != null;
    }

    public String normalizedKeyword() {
        return StringUtils.hasText(keyword) ? keyword.strip() : null;
    }

    public void validate() {
        boolean hasLatitude = centerLat != null;
        boolean hasLongitude = centerLng != null;

        if (hasLatitude != hasLongitude
                || page < 0
                || size < 1
                || size > MAX_PAGE_SIZE
                || radiusMeters < MIN_RADIUS_METERS
                || radiusMeters > MAX_RADIUS_METERS) {
            throw invalidRequest();
        }

        if (hasLocation()
                && (centerLat.compareTo(MIN_LATITUDE) < 0
                || centerLat.compareTo(MAX_LATITUDE) > 0
                || centerLng.compareTo(MIN_LONGITUDE) < 0
                || centerLng.compareTo(MAX_LONGITUDE) > 0)) {
            throw invalidRequest();
        }
    }

    private BusinessLogicException invalidRequest() {
        return new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
    }
}

package com.namatdang.namatdang.store.dto;

import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class StoreCreateRequestDto {

    @NotBlank(message = "매장명은 필수 입력 값입니다.")
    @Size(max = 100, message = "매장명은 100자 이하로 입력해 주세요.")
    private String name;

    @NotBlank(message = "주소는 필수 입력 값입니다.")
    @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
    private String address;

    @Size(max = 255, message = "상세 주소는 255자 이하로 입력해 주세요.")
    private String addressDetail;

    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phoneNumber;

    @Size(max = 1000, message = "매장 설명은 1000자 이하로 입력해 주세요.")
    private String description;

    @DecimalMin(value = "-90.0", message = "위도 범위를 확인해 주세요.")
    @DecimalMax(value = "90.0", message = "위도 범위를 확인해 주세요.")
    @Digits(integer = 3, fraction = 7, message = "위도는 소수점 7자리 이하로 입력해 주세요.")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "경도 범위를 확인해 주세요.")
    @DecimalMax(value = "180.0", message = "경도 범위를 확인해 주세요.")
    @Digits(integer = 3, fraction = 7, message = "경도는 소수점 7자리 이하로 입력해 주세요.")
    private BigDecimal longitude;

    public Store toEntity(User owner) {
        return new Store(owner,
                         name,
                         address,
                         addressDetail,
                         phoneNumber,
                         description,
                         latitude,
                         longitude);
    }
}

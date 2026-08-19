package com.namatdang.namatdang.deal.dto;

import com.namatdang.namatdang.deal.entity.DealItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class DealItemCreateRequestDto {

    @NotBlank(message = "상품명은 필수 입력 값입니다.")
    @Size(max = 50, message = "상품명은 50자 이하로 입력해 주세요.")
    private String name;

    @NotNull(message = "등록 수량은 필수 입력 값입니다.")
    @Min(value = 1, message = "등록 수량은 1개 이상이어야 합니다.")
    @Max(value = 99, message = "등록 수량은 99개 이하여야 합니다.")
    private Integer totalQuantity;

    @NotNull(message = "정가는 필수 입력 값입니다.")
    @Min(value = 100, message = "정가는 100원 이상이어야 합니다.")
    @Max(value = 1000000, message = "정가는 1,000,000원 이하여야 합니다.")
    private Integer originalPrice;

    @NotNull(message = "판매가는 필수 입력 값입니다.")
    @Min(value = 100, message = "판매가는 100원 이상이어야 합니다.")
    @Max(value = 1000000, message = "판매가는 1,000,000원 이하여야 합니다.")
    private Integer salePrice;

    public DealItem toEntity() {
        return new DealItem(name, totalQuantity, originalPrice, salePrice);
    }
}

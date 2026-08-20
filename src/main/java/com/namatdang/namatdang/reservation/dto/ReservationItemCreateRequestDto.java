package com.namatdang.namatdang.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReservationItemCreateRequestDto {

    @NotNull(message = "품목 ID는 필수 입력 값입니다.")
    private Long dealItemId;

    @NotNull(message = "예약 수량은 필수 입력 값입니다.")
    @Min(value = 1, message = "예약 수량은 1개 이상이어야 합니다.")
    @Max(value = 10, message = "예약 수량은 10개 이하여야 합니다.")
    private Integer quantity;
}

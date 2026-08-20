package com.namatdang.namatdang.deal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class DealCreateRequestDto {

    @NotNull(message = "판매 마감시각은 필수 입력 값입니다.")
    private LocalDateTime salesEndsAt;

    @Size(max = 500, message = "안내사항은 500자 이하로 입력해 주세요.")
    private String description;

    @NotEmpty(message = "품목은 1종 이상 등록해 주세요.")
    @Size(min = 1, max = 10, message = "품목은 1~10종까지 등록할 수 있습니다.")
    @Valid
    private List<DealItemCreateRequestDto> items;
}

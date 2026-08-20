package com.namatdang.namatdang.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReservationCreateRequestDto {

    @NotNull(message = "딜 ID는 필수 입력 값입니다.")
    private Long dealId;

    @NotEmpty(message = "예약할 품목을 1개 이상 선택해 주세요.")
    @Size(max = 10, message = "한 번에 예약할 수 있는 품목은 10개까지입니다.")
    @Valid
    private List<ReservationItemCreateRequestDto> items;

    /**
     * 멱등 해시의 입력 문자열. JSON 배열 순서처럼 업무 의미가 없는 차이로 키 충돌이 나지 않도록
     * 품목을 dealItemId 오름차순으로 정렬한 뒤 직렬화한다.
     */
    public String toCanonicalForm() {
        String canonicalItems = items.stream()
                .sorted(Comparator.comparing(ReservationItemCreateRequestDto::getDealItemId))
                .map(item -> item.getDealItemId() + ":" + item.getQuantity())
                .collect(Collectors.joining(","));

        return dealId + "|" + canonicalItems;
    }
}

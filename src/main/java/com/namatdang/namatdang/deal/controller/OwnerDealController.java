package com.namatdang.namatdang.deal.controller;

import com.namatdang.namatdang.deal.dto.DealCreateRequestDto;
import com.namatdang.namatdang.deal.dto.DealDetailResponseDto;
import com.namatdang.namatdang.deal.dto.DealPageResponseDto;
import com.namatdang.namatdang.deal.entity.DealStatus;
import com.namatdang.namatdang.deal.service.OwnerDealService;
import com.namatdang.namatdang.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner")
@Tag(name = "딜 관리", description = "사장님 딜 등록 및 조회 API")
// TODO: #7 - 딜 수정·취소 엔드포인트가 아직 없다. 다음 작업으로 아래를 추가한다.
//   - PATCH /owner/deals/{dealId}         : 판매 마감시각·안내사항 수정 (DEAL-08)
//   - PATCH /owner/deal-items/{dealItemId}: 상품명·정가·판매가·등록 수량 수정
//     (DR-11: 등록 수량은 예약·판매 반영 수량보다 작게 줄일 수 없고, 품목 추가·삭제는 불가)
//   - POST  /owner/deals/{dealId}/cancel  : 유효 예약이 없을 때만 취소 (DEAL-03)
public class OwnerDealController {

    private final OwnerDealService ownerDealService;

    @PostMapping("/stores/{storeId}/deals")
    @Operation(summary = "딜 등록")
    public ResponseEntity<DealDetailResponseDto> createDeal(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long storeId,
            // TODO: #28 - 헤더를 받기만 하고 쓰지 않아, 같은 X-Request-Id로 재시도하면
            //  딜이 중복 생성된다. 멱등키 저장소를 도입할 때 이 파라미터를 서비스로 넘긴다.
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody DealCreateRequestDto requestDto
    ) {
        DealDetailResponseDto responseDto = ownerDealService.createDeal(authUser.getUserId(), storeId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping("/stores/{storeId}/deals")
    @Operation(summary = "내 매장의 딜 목록 조회")
    public ResponseEntity<DealPageResponseDto> getMyStoreDeals(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long storeId,
            @RequestParam(required = false) DealStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DealPageResponseDto responseDto =
                ownerDealService.getMyStoreDeals(authUser.getUserId(), storeId, status, page, size);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/deals/{dealId}")
    @Operation(summary = "내 딜 상세 조회")
    public ResponseEntity<DealDetailResponseDto> getMyDeal(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long dealId) {
        DealDetailResponseDto responseDto = ownerDealService.getMyDeal(authUser.getUserId(), dealId);
        return ResponseEntity.ok(responseDto);
    }
}

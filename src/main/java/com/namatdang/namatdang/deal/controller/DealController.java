package com.namatdang.namatdang.deal.controller;

import com.namatdang.namatdang.deal.dto.DealDetailResponseDto;
import com.namatdang.namatdang.deal.dto.DealPageResponseDto;
import com.namatdang.namatdang.deal.dto.DealSearchRequestDto;
import com.namatdang.namatdang.deal.service.DealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deals")
@Tag(name = "딜 조회", description = "판매 중 딜 목록 및 상세 조회 API")
public class DealController {

    private final DealService dealService;

    @GetMapping
    @Operation(summary = "판매 중 딜 목록 조회 및 검색")
    public ResponseEntity<DealPageResponseDto> getDeals(@Valid @ModelAttribute DealSearchRequestDto requestDto) {
        DealPageResponseDto responseDto = dealService.getSellingDeals(requestDto);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{dealId}")
    @Operation(summary = "딜 상세 조회")
    public ResponseEntity<DealDetailResponseDto> getDealDetail(@PathVariable Long dealId) {
        DealDetailResponseDto responseDto = dealService.getDeal(dealId);
        return ResponseEntity.ok(responseDto);
    }
}

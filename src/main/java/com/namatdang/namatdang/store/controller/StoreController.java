package com.namatdang.namatdang.store.controller;

import com.namatdang.namatdang.store.dto.StorePageResponseDto;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import com.namatdang.namatdang.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stores")
@Tag(name = "매장 조회", description = "매장 검색 및 상세 조회 API")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    @Operation(summary = "매장 목록 조회 및 검색")
    public ResponseEntity<StorePageResponseDto> getStores(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        StorePageResponseDto responseDto = storeService.getStores(keyword, page, size);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{storeId}")
    @Operation(summary = "매장 상세 조회")
    public ResponseEntity<StoreResponseDto> getStoreDetail(@PathVariable Long storeId) {
        StoreResponseDto responseDto = storeService.getStore(storeId);
        return ResponseEntity.ok(responseDto);
    }
}

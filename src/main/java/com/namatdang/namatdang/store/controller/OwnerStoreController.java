package com.namatdang.namatdang.store.controller;

import com.namatdang.namatdang.store.dto.StoreCreateRequestDto;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import com.namatdang.namatdang.store.dto.StoreUpdateRequestDto;
import com.namatdang.namatdang.store.service.OwnerStoreService;
import com.namatdang.namatdang.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner/stores")
@Tag(name = "사장님 매장 관리", description = "사장님의 매장 등록·조회·수정 API")
public class OwnerStoreController {

    private final OwnerStoreService ownerStoreService;

    @PostMapping
    @Operation(summary = "매장 등록")
    public ResponseEntity<StoreResponseDto> createStore(@AuthenticationPrincipal AuthUser authUser,
                                                        @Valid @RequestBody StoreCreateRequestDto requestDto) {
        StoreResponseDto responseDto = ownerStoreService.createStore(authUser.getUserId(), requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping
    @Operation(summary = "내 매장 목록 조회")
    public ResponseEntity<List<StoreResponseDto>> getMyStores(@AuthenticationPrincipal AuthUser authUser) {
        List<StoreResponseDto> responseDtos = ownerStoreService.getMyStores(authUser.getUserId());
        return ResponseEntity.ok(responseDtos);
    }

    @PatchMapping("/{storeId}")
    @Operation(summary = "내 매장 정보 수정")
    public ResponseEntity<StoreResponseDto> updateStore(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long storeId,
                                                        @Valid @RequestBody StoreUpdateRequestDto requestDto) {
        StoreResponseDto responseDto = ownerStoreService.updateStore(authUser.getUserId(), storeId, requestDto);
        return ResponseEntity.ok(responseDto);
    }
}

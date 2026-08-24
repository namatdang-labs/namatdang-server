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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner/stores")
@Tag(name = "사장님 매장 관리", description = "사장님의 매장 등록·조회·수정 API")
public class OwnerStoreController {

    private final OwnerStoreService ownerStoreService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "매장 등록")
    public ResponseEntity<StoreResponseDto> createStore(@AuthenticationPrincipal AuthUser authUser,
                                                        @Valid @RequestBody StoreCreateRequestDto requestDto) {
        StoreResponseDto responseDto = ownerStoreService.createStore(authUser.getUserId(), requestDto, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "대표 이미지와 함께 매장 등록")
    public ResponseEntity<StoreResponseDto> createStoreWithImage(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestPart("request") StoreCreateRequestDto requestDto,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        StoreResponseDto responseDto = ownerStoreService.createStore(authUser.getUserId(), requestDto, image);
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

    @PutMapping(value = "/{storeId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "내 매장 대표 이미지 등록·교체")
    public ResponseEntity<StoreResponseDto> updateStoreImage(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long storeId,
            @RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(ownerStoreService.updateImage(authUser.getUserId(), storeId, image));
    }

    @DeleteMapping("/{storeId}/image")
    @Operation(summary = "내 매장 대표 이미지 삭제")
    public ResponseEntity<Void> deleteStoreImage(@AuthenticationPrincipal AuthUser authUser,
                                                 @PathVariable Long storeId) {
        ownerStoreService.deleteImage(authUser.getUserId(), storeId);
        return ResponseEntity.noContent().build();
    }
}

package com.namatdang.namatdang.favorite.controller;

import com.namatdang.namatdang.favorite.service.FavoriteService;
import com.namatdang.namatdang.store.dto.StoreResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/favorites")
@Tag(name = "매장 즐겨찾기", description = "소비자의 매장 즐겨찾기 관리 API")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    @Operation(summary = "즐겨찾기 매장 목록 조회")
    public ResponseEntity<List<StoreResponseDto>> getFavorites(@RequestAttribute("userId") Long userId) {
        List<StoreResponseDto> responseDtos = favoriteService.getFavorites(userId);
        return ResponseEntity.ok(responseDtos);
    }

    @PutMapping("/{storeId}")
    @Operation(summary = "매장 즐겨찾기 등록")
    public ResponseEntity<Void> addFavorite(@RequestAttribute("userId") Long userId,
                                            @PathVariable Long storeId) {
        favoriteService.addFavorite(userId, storeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{storeId}")
    @Operation(summary = "매장 즐겨찾기 해제")
    public ResponseEntity<Void> deleteFavorite(@RequestAttribute("userId") Long userId,
                                               @PathVariable Long storeId) {
        favoriteService.deleteFavorite(userId, storeId);
        return ResponseEntity.noContent().build();
    }
}

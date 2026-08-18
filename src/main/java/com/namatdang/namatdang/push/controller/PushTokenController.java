package com.namatdang.namatdang.push.controller;

import com.namatdang.namatdang.push.dto.PushTokenRegisterRequestDto;
import com.namatdang.namatdang.push.dto.PushTokenResponseDto;
import com.namatdang.namatdang.push.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push-tokens")
@Tag(name = "Push 토큰", description = "사용자 기기의 FCM Registration Token 관리 API")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PutMapping
    @Operation(summary = "FCM Push 토큰 등록 또는 갱신")
    public ResponseEntity<PushTokenResponseDto> register(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody PushTokenRegisterRequestDto request
    ) {
        PushTokenResponseDto response = pushTokenService.register(userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{pushTokenId}")
    @Operation(summary = "FCM Push 토큰 삭제")
    public ResponseEntity<Void> delete(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long pushTokenId
    ) {
        pushTokenService.delete(userId, pushTokenId);
        return ResponseEntity.noContent().build();
    }
}

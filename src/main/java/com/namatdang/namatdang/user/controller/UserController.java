package com.namatdang.namatdang.user.controller;

import com.namatdang.namatdang.security.AuthUser;
import com.namatdang.namatdang.user.dto.UserResponseDto;
import com.namatdang.namatdang.user.dto.UserUpdateRequestDto;
import com.namatdang.namatdang.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "회원", description = "내 정보 관리 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public ResponseEntity<UserResponseDto> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {
        UserResponseDto response = userService.getUser(authUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정")
    public ResponseEntity<UserResponseDto> updateMyInfo(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UserUpdateRequestDto userUpdateRequestDto
    ) {
        UserResponseDto response = userService.updateUser(authUser.getUserId(), userUpdateRequestDto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal AuthUser authUser) {
        userService.deleteUser(authUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}

package com.namatdang.namatdang.user.controller;

import com.namatdang.namatdang.user.dto.UserResponseDto;
import com.namatdang.namatdang.user.dto.UserSignUpRequestDto;
import com.namatdang.namatdang.user.dto.UserSignUpResponseDto;
import com.namatdang.namatdang.user.dto.UserUpdateRequestDto;
import com.namatdang.namatdang.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "회원", description = "회원가입 및 내 정보 관리 API")
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    public ResponseEntity<UserSignUpResponseDto> signUp(@Valid @RequestBody UserSignUpRequestDto userSignUpRequestDto) {
        UserSignUpResponseDto response = userService.signUpUser(userSignUpRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public ResponseEntity<UserResponseDto> getMyInfo(@RequestAttribute("userId") Long userId) {
        UserResponseDto response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정")
    public ResponseEntity<UserResponseDto> updateMyInfo(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody UserUpdateRequestDto userUpdateRequestDto
    ) {
        UserResponseDto response = userService.updateUser(userId, userUpdateRequestDto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    public ResponseEntity<Void> deleteUser(@RequestAttribute("userId") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}

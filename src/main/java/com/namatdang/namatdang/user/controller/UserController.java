package com.namatdang.namatdang.user.controller;

import com.namatdang.namatdang.user.dto.UserResponseDto;
import com.namatdang.namatdang.user.dto.UserUpdateRequestDto;
import com.namatdang.namatdang.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyInfo(@RequestAttribute("userId") Long userId) {
        UserResponseDto response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDto> updateMyInfo(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody UserUpdateRequestDto userUpdateRequestDto
    ) {
        UserResponseDto response = userService.updateUser(userId, userUpdateRequestDto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(@RequestAttribute("userId") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}

package com.namatdang.namatdang.auth.controller;

import com.namatdang.namatdang.user.dto.UserSignUpRequestDto;
import com.namatdang.namatdang.user.dto.UserSignUpResponseDto;
import com.namatdang.namatdang.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<UserSignUpResponseDto> signUp(
            @Valid @RequestBody UserSignUpRequestDto userSignUpRequestDto
    ) {
        UserSignUpResponseDto response = userService.signUpUser(userSignUpRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

package com.namatdang.namatdang.auth.dto;

import com.namatdang.namatdang.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDto {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private LoginUserResponseDto user;

    public static LoginResponseDto of(String accessToken, long expiresIn, User user) {
        return new LoginResponseDto(
                accessToken,
                "Bearer",
                expiresIn,
                LoginUserResponseDto.from(user)
        );
    }
}

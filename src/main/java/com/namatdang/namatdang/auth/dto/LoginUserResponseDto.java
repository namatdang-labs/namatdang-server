package com.namatdang.namatdang.auth.dto;

import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginUserResponseDto {

    private Long id;
    private String email;
    private String name;
    private UserRole role;

    public static LoginUserResponseDto from(User user) {
        return new LoginUserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
    }
}

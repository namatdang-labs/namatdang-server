package com.namatdang.namatdang.user.dto;

import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSignUpResponseDto {

    private Long id;
    private String email;
    private String name;
    private String phoneNumber;
    private UserRole role;

    public static UserSignUpResponseDto from(User user) {
        return new UserSignUpResponseDto(user.getId(),
                                         user.getEmail(),
                                         user.getName(),
                                         user.getPhoneNumber(),
                                         user.getRole());
    }
}

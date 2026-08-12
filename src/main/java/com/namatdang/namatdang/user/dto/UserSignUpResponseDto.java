package com.namatdang.namatdang.user.dto;

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
}

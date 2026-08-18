package com.namatdang.namatdang.security;

import com.namatdang.namatdang.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthUser {

    private Long userId;
    private UserRole role;
}

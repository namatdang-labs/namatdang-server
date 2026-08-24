package com.namatdang.namatdang.security;

import com.namatdang.namatdang.user.entity.UserRole;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthUser {

    private Long userId;
    private Set<UserRole> roles;
}

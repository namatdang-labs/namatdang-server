package com.namatdang.namatdang.user.dto;

import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String email;
    private String name;
    private String phoneNumber;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponseDto from(User user) {
        return new UserResponseDto(user.getId(),
                                   user.getEmail(),
                                   user.getName(),
                                   user.getPhoneNumber(),
                                   user.getRole(),
                                   user.getCreatedAt(),
                                   user.getUpdatedAt());
    }
}

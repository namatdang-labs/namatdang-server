package com.namatdang.namatdang.user.dto;

import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String email;
    private String name;
    private String phoneNumber;
    private List<UserRole> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponseDto from(User user) {
        return new UserResponseDto(user.getId(),
                                   user.getEmail(),
                                   user.getName(),
                                   user.getPhoneNumber(),
                                   user.getRoles().stream().toList(),
                                   user.getCreatedAt(),
                                   user.getUpdatedAt());
    }
}

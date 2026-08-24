package com.namatdang.namatdang.user.dto;

import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSignUpResponseDto {

    private Long id;
    private String email;
    private String name;
    private String phoneNumber;
    private List<UserRole> roles;

    public static UserSignUpResponseDto from(User user) {
        return new UserSignUpResponseDto(user.getId(),
                                         user.getEmail(),
                                         user.getName(),
                                         user.getPhoneNumber(),
                                         user.getRoles().stream().toList());
    }
}

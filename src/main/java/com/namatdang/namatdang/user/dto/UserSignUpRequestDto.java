package com.namatdang.namatdang.user.dto;

import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Getter;

@Getter
public class UserSignUpRequestDto {

    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
    private String password;

    @NotBlank(message = "이름은 필수 입력 값입니다.")
    @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    private String name;

    @NotBlank(message = "전화번호는 필수 입력 값입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phoneNumber;

    @NotNull(message = "역할은 필수 입력 값입니다.")
    private UserRole role;

    public User toEntity(String encodedPassword) {
        return new User(
                email.strip().toLowerCase(Locale.ROOT),
                encodedPassword,
                name.strip(),
                phoneNumber.strip(),
                role
        );
    }
}

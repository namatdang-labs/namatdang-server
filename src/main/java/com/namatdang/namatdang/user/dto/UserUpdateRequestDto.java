package com.namatdang.namatdang.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UserUpdateRequestDto {

    @Pattern(regexp = ".*\\S.*", message = "이름은 비워둘 수 없습니다.")
    @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    private String name;

    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phoneNumber;

    public boolean hasUpdates() {
        return name != null || phoneNumber != null;
    }
}

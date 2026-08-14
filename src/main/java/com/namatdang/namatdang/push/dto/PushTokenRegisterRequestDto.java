package com.namatdang.namatdang.push.dto;

import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.entity.PushDeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Getter;

@Getter
public class PushTokenRegisterRequestDto {

    @NotBlank(message = "FCM 등록 토큰은 필수 입력 값입니다.")
    @Size(max = 512, message = "FCM 등록 토큰은 512자 이하로 입력해 주세요.")
    private String registrationToken;

    @NotNull(message = "기기 유형은 필수 입력 값입니다.")
    private PushDeviceType deviceType;

    @NotBlank(message = "브라우저 정보는 필수 입력 값입니다.")
    @Size(max = 30, message = "브라우저 정보는 30자 이하로 입력해 주세요.")
    private String browser;

    public String normalizedRegistrationToken() {
        return registrationToken.strip();
    }

    public String normalizedBrowser() {
        return browser.strip().toUpperCase(Locale.ROOT);
    }

    public FcmRegistration toEntity(Long userId) {
        return new FcmRegistration(
                userId,
                normalizedRegistrationToken(),
                deviceType,
                normalizedBrowser()
        );
    }
}

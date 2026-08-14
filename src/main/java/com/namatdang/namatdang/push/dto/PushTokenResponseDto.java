package com.namatdang.namatdang.push.dto;

import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.entity.PushDeviceType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PushTokenResponseDto {

    private Long id;
    private PushDeviceType deviceType;
    private String browser;
    private boolean active;
    private LocalDateTime lastRegisteredAt;

    public static PushTokenResponseDto from(FcmRegistration registration) {
        return new PushTokenResponseDto(
                registration.getId(),
                registration.getDeviceType(),
                registration.getBrowser(),
                registration.isActive(),
                registration.getLastRegisteredAt()
        );
    }
}

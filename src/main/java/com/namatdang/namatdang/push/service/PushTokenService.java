package com.namatdang.namatdang.push.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.push.dto.PushTokenRegisterRequestDto;
import com.namatdang.namatdang.push.dto.PushTokenResponseDto;
import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.repository.FcmRegistrationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final FcmRegistrationRepository fcmRegistrationRepository;

    @Transactional
    public PushTokenResponseDto register(Long userId, PushTokenRegisterRequestDto request) {
        String registrationToken = request.normalizedRegistrationToken();
        String browser = request.normalizedBrowser();

        FcmRegistration registration = fcmRegistrationRepository.findByRegistrationToken(registrationToken)
                .map(existingRegistration -> {
                    existingRegistration.register(userId, request.getDeviceType(), browser);
                    return existingRegistration;
                })
                .orElseGet(() -> request.toEntity(userId));

        fcmRegistrationRepository.saveAndFlush(registration);
        return PushTokenResponseDto.from(registration);
    }

    @Transactional
    public void deactivate(Long userId, Long pushTokenId) {
        FcmRegistration registration = fcmRegistrationRepository.findByIdAndUserId(pushTokenId, userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.PUSH_TOKEN_NOT_FOUND));

        registration.deactivate();
    }

    @Transactional
    public void deactivateInvalidToken(String registrationToken) {
        fcmRegistrationRepository.findByRegistrationToken(registrationToken)
                .ifPresent(FcmRegistration::deactivate);
    }

    @Transactional(readOnly = true)
    public List<FcmRegistration> getActiveRegistrations(Long userId) {
        return fcmRegistrationRepository.findAllByUserIdAndActiveTrueOrderByIdAsc(userId);
    }
}

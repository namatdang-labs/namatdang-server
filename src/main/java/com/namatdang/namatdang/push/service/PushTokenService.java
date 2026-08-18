package com.namatdang.namatdang.push.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.push.dto.PushTokenRegisterRequestDto;
import com.namatdang.namatdang.push.dto.PushTokenResponseDto;
import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.repository.FcmRegistrationRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final FcmRegistrationRepository fcmRegistrationRepository;
    private final UserRepository userRepository;

    @Transactional
    public PushTokenResponseDto register(Long userId, PushTokenRegisterRequestDto request) {
        String registrationToken = request.normalizedRegistrationToken();
        String browser = request.normalizedBrowser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.USER_NOT_FOUND));

        FcmRegistration registration = fcmRegistrationRepository.findByRegistrationToken(registrationToken)
                .map(existingRegistration -> {
                    existingRegistration.register(user, request.getDeviceType(), browser);
                    return existingRegistration;
                })
                .orElseGet(() -> request.toEntity(user));

        fcmRegistrationRepository.saveAndFlush(registration);
        return PushTokenResponseDto.from(registration);
    }

    @Transactional
    public void delete(Long userId, Long pushTokenId) {
        FcmRegistration registration = fcmRegistrationRepository.findByIdAndUser_Id(pushTokenId, userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.PUSH_TOKEN_NOT_FOUND));

        fcmRegistrationRepository.delete(registration);
    }

    @Transactional
    public void deleteInvalidToken(String registrationToken) {
        fcmRegistrationRepository.findByRegistrationToken(registrationToken)
                .ifPresent(fcmRegistrationRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<FcmRegistration> getRegistrations(Long userId) {
        return fcmRegistrationRepository.findAllByUser_IdOrderByIdAsc(userId);
    }
}

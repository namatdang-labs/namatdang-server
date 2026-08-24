package com.namatdang.namatdang.push.adapter;

import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.push.InvalidPushRegistrationHandler;
import com.namatdang.namatdang.notification.push.PushTarget;
import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.repository.FcmRegistrationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FcmRegistrationNotificationAdapter
        implements ActivePushRegistrationReader, InvalidPushRegistrationHandler {

    private final FcmRegistrationRepository fcmRegistrationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PushTarget> findAllByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        return fcmRegistrationRepository.findAllByUser_IdInOrderByIdAsc(userIds).stream()
                .map(this::toPushTarget)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PushTarget> findById(Long registrationId) {
        return fcmRegistrationRepository.findById(registrationId)
                .map(this::toPushTarget);
    }

    @Override
    @Transactional
    public void invalidate(Long registrationId) {
        fcmRegistrationRepository.deleteInvalidRegistration(registrationId);
    }

    private PushTarget toPushTarget(FcmRegistration registration) {
        return new PushTarget(
                registration.getId(),
                registration.getUserId(),
                registration.getRegistrationToken()
        );
    }
}

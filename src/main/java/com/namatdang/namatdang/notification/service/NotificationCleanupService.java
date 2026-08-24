package com.namatdang.namatdang.notification.service;

import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    private static final int RETENTION_DAYS = 30;

    private final NotificationRepository notificationRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final NotificationEventConsumptionRepository consumptionRepository;

    @Scheduled(cron = "${notification.cleanup.cron:0 0 0 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        pushDeliveryRepository.deleteAllByNotificationCreatedBefore(cutoff);
        notificationRepository.deleteAllCreatedBefore(cutoff);
        consumptionRepository.deleteAllProcessedBefore(cutoff);
    }
}

package com.namatdang.namatdang.notification.delivery.service;

import com.namatdang.namatdang.notification.delivery.config.NotificationPushProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notification.push.enabled",
        havingValue = "true"
)
public class PushDeliveryScheduler {

    private final PushDeliveryProcessor pushDeliveryProcessor;
    private final NotificationPushProperties properties;

    @Scheduled(fixedDelayString = "${notification.push.worker-fixed-delay-ms:1000}")
    public void process() {
        pushDeliveryProcessor.processBatch(
                properties.getBatchSize(),
                properties.getSendingTimeout(),
                properties.getRetryDelay(),
                properties.getMaxAttemptCount()
        );
    }
}

package com.namatdang.namatdang.notification.outbox.service;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventPublisher;
import com.namatdang.namatdang.notification.messaging.sqs.NotificationSqsProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notification.messaging.sqs.enabled",
        havingValue = "true"
)
public class NotificationOutboxPublisher {

    private final NotificationOutboxService notificationOutboxService;
    private final NotificationEventPublisher notificationEventPublisher;
    private final NotificationSqsProperties properties;

    @Scheduled(
            fixedDelayString = "${notification.messaging.sqs.publisher-fixed-delay-ms:1000}"
    )
    public void publishScheduledBatch() {
        publishBatch();
    }

    public int publishBatch() {
        List<NotificationEventMessage> messages = notificationOutboxService.claimPublishableEvents(
                properties.getPublishBatchSize(),
                properties.getPublishingTimeout()
        );
        messages.forEach(this::publish);
        return messages.size();
    }

    private void publish(NotificationEventMessage message) {
        try {
            notificationEventPublisher.publish(message);
            notificationOutboxService.markPublished(message.eventId());
        } catch (RuntimeException exception) {
            log.warn("Outbox 알림 이벤트 발행 실패. eventId={}", message.eventId(), exception);
            notificationOutboxService.recordPublishFailure(
                    message.eventId(),
                    errorMessage(exception),
                    properties.getPublishRetryDelay(),
                    properties.getPublishMaxAttemptCount()
            );
        }
    }

    private String errorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}

package com.namatdang.namatdang.notification.messaging.sqs;

import static com.namatdang.namatdang.notification.messaging.sqs.NotificationSqsConfiguration.SQS_POLLING_TASK_SCHEDULER;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "notification.messaging.sqs",
        name = {"enabled", "consumer-enabled"},
        havingValue = "true"
)
public class SqsNotificationEventPollingScheduler {

    private final SqsNotificationEventConsumer consumer;

    @Scheduled(
            fixedDelayString = "${notification.messaging.sqs.consumer-fixed-delay-ms:1000}",
            scheduler = SQS_POLLING_TASK_SCHEDULER
    )
    public void poll() {
        consumer.pollOnce();
    }
}

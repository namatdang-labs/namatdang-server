package com.namatdang.namatdang.notification.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SqsPollingSchedulerConfigurationTests {

    @Test
    void useDedicatedSingleThreadSchedulerForLongPolling() throws Exception {
        ThreadPoolTaskScheduler taskScheduler =
                new NotificationSqsConfiguration.ConsumerConfiguration().sqsPollingTaskScheduler();
        taskScheduler.initialize();

        try {
            assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isOne();
            assertThat(taskScheduler.getThreadNamePrefix()).isEqualTo("sqs-notification-poller-");

            Scheduled scheduled = SqsNotificationEventPollingScheduler.class
                    .getDeclaredMethod("poll")
                    .getAnnotation(Scheduled.class);
            assertThat(scheduled.scheduler())
                    .isEqualTo(NotificationSqsConfiguration.SQS_POLLING_TASK_SCHEDULER);
        } finally {
            taskScheduler.destroy();
        }
    }
}

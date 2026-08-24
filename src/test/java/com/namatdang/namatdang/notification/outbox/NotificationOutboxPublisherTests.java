package com.namatdang.namatdang.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventPublisher;
import com.namatdang.namatdang.notification.messaging.sqs.NotificationSqsProperties;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEvent;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEventStatus;
import com.namatdang.namatdang.notification.outbox.repository.NotificationEventRepository;
import com.namatdang.namatdang.notification.outbox.service.NotificationOutboxPublisher;
import com.namatdang.namatdang.notification.outbox.service.NotificationOutboxService;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificationOutboxPublisherTests extends IntegrationTestSupport {

    @Autowired
    private NotificationOutboxService outboxService;

    @Autowired
    private NotificationEventRepository eventRepository;

    private RecordingPublisher eventPublisher;
    private NotificationOutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        eventPublisher = new RecordingPublisher();
        NotificationSqsProperties properties = new NotificationSqsProperties();
        properties.setPublishBatchSize(10);
        properties.setPublishingTimeout(Duration.ofMinutes(5));
        properties.setPublishRetryDelay(Duration.ZERO);
        properties.setPublishMaxAttemptCount(3);
        outboxPublisher = new NotificationOutboxPublisher(
                outboxService,
                eventPublisher,
                properties
        );
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
    }

    @Test
    void markOutboxEventPublishedAfterSqsSuccess() {
        recordDealCreated(7_001L, 8_001L);

        assertThat(outboxPublisher.publishBatch()).isEqualTo(1);

        NotificationEvent event = eventRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(eventPublisher.messages).hasSize(1);
    }

    @Test
    void returnFailedSqsPublishToRetryQueue() {
        recordDealCreated(7_002L, 8_002L);
        eventPublisher.fail = true;

        assertThat(outboxPublisher.publishBatch()).isEqualTo(1);

        NotificationEvent event = eventRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("fake SQS failure");
    }

    private void recordDealCreated(Long dealId, Long storeId) {
        outboxService.recordDealCreated(dealId, storeId, LocalDateTime.now());
    }

    private static class RecordingPublisher implements NotificationEventPublisher {

        private final List<NotificationEventMessage> messages = new ArrayList<>();
        private boolean fail;

        @Override
        public void publish(NotificationEventMessage message) {
            if (fail) {
                throw new IllegalStateException("fake SQS failure");
            }
            messages.add(message);
        }
    }
}

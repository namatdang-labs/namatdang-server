package com.namatdang.namatdang.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEvent;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEventStatus;
import com.namatdang.namatdang.notification.outbox.repository.NotificationEventRepository;
import com.namatdang.namatdang.notification.outbox.service.NotificationOutboxService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NotificationOutboxServiceTests {

    private static final Duration PUBLISHING_TIMEOUT = Duration.ofMinutes(5);

    @Autowired
    private NotificationOutboxService notificationOutboxService;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void recordDealCreatedIdempotently() {
        LocalDateTime dealCreatedAt = LocalDateTime.now();
        notificationOutboxService.recordDealCreated(101L, 11L, dealCreatedAt);
        notificationOutboxService.recordDealCreated(101L, 11L, dealCreatedAt);

        List<NotificationEvent> events = notificationEventRepository.findAll();
        assertThat(events).hasSize(1);

        NotificationEvent event = events.getFirst();
        assertThat(event.getDealId()).isEqualTo(101L);
        assertThat(event.getStoreId()).isEqualTo(11L);
        assertThat(event.getReservationId()).isNull();
        assertThat(event.getEventType()).isEqualTo(NotificationEventType.DEAL_CREATED);
        assertThat(event.getSourceRequestKey()).isEqualTo("DEAL:101:CREATED");
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void duplicatedSourceRequestKeyCannotBeSavedDirectly() {
        notificationEventRepository.saveAndFlush(
                NotificationEvent.dealCreated(102L, 12L, LocalDateTime.now())
        );

        assertThatThrownBy(() -> notificationEventRepository.saveAndFlush(
                NotificationEvent.dealCreated(102L, 12L, LocalDateTime.now())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void claimPendingEventsInIdOrderWithinBatchSize() {
        recordDealCreated(201L, 21L);
        recordDealCreated(202L, 22L);
        recordDealCreated(203L, 23L);

        List<NotificationEventMessage> claimedEvents = notificationOutboxService
                .claimPublishableEvents(2, PUBLISHING_TIMEOUT);

        assertThat(claimedEvents).hasSize(2);
        assertThat(claimedEvents)
                .extracting(NotificationEventMessage::dealId)
                .containsExactly(201L, 202L);
        assertThat(claimedEvents)
                .allSatisfy(message -> {
                    assertThat(message.schemaVersion()).isEqualTo(1);
                    assertThat(message.eventId()).isNotNull();
                    assertThat(message.eventType()).isEqualTo(NotificationEventType.DEAL_CREATED);
                    assertThat(message.occurredAt()).isNotNull();
                });

        List<NotificationEvent> events = notificationEventRepository.findAll(Sort.by("id"));
        assertThat(events)
                .extracting(NotificationEvent::getStatus)
                .containsExactly(
                        NotificationEventStatus.PUBLISHING,
                        NotificationEventStatus.PUBLISHING,
                        NotificationEventStatus.PENDING
                );
    }

    @Test
    void markClaimedEventAsPublished() {
        recordDealCreated(301L, 31L);
        Long eventId = claimSingleEvent();

        notificationOutboxService.markPublished(eventId);

        NotificationEvent event = notificationEventRepository.findById(eventId).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getPublishingStartedAt()).isNull();
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void retryFailureAndStopAfterMaximumAttempts() {
        recordDealCreated(401L, 41L);
        Long eventId = claimSingleEvent();

        notificationOutboxService.recordPublishFailure(
                eventId,
                "temporary SQS error",
                Duration.ZERO,
                2
        );

        NotificationEvent retryableEvent = notificationEventRepository.findById(eventId).orElseThrow();
        assertThat(retryableEvent.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
        assertThat(retryableEvent.getRetryCount()).isEqualTo(1);
        assertThat(retryableEvent.getNextRetryAt()).isNotNull();
        assertThat(retryableEvent.getLastError()).isEqualTo("temporary SQS error");

        claimSingleEvent();
        notificationOutboxService.recordPublishFailure(
                eventId,
                "permanent SQS error",
                Duration.ZERO,
                2
        );

        NotificationEvent failedEvent = notificationEventRepository.findById(eventId).orElseThrow();
        assertThat(failedEvent.getStatus()).isEqualTo(NotificationEventStatus.FAILED);
        assertThat(failedEvent.getRetryCount()).isEqualTo(2);
        assertThat(failedEvent.getNextRetryAt()).isNull();
        assertThat(failedEvent.getLastError()).isEqualTo("permanent SQS error");
    }

    @Test
    void doNotClaimFreshPublishingEventAgain() {
        recordDealCreated(501L, 51L);
        claimSingleEvent();

        List<NotificationEventMessage> claimedAgain = notificationOutboxService
                .claimPublishableEvents(1, PUBLISHING_TIMEOUT);

        assertThat(claimedAgain).isEmpty();
    }

    @Test
    void reclaimPublishingEventAfterTimeout() {
        recordDealCreated(601L, 61L);
        Long eventId = claimSingleEvent();
        jdbcTemplate.update(
                "UPDATE notification_events SET publishing_started_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
                eventId
        );
        entityManager.clear();

        List<NotificationEventMessage> reclaimedEvents = notificationOutboxService
                .claimPublishableEvents(1, PUBLISHING_TIMEOUT);

        assertThat(reclaimedEvents)
                .extracting(NotificationEventMessage::eventId)
                .containsExactly(eventId);
        NotificationEvent reclaimedEvent = notificationEventRepository.findById(eventId).orElseThrow();
        assertThat(reclaimedEvent.getStatus()).isEqualTo(NotificationEventStatus.PUBLISHING);
        assertThat(reclaimedEvent.getPublishingStartedAt())
                .isAfter(LocalDateTime.now().minusMinutes(1));
    }

    private Long claimSingleEvent() {
        return notificationOutboxService.claimPublishableEvents(1, PUBLISHING_TIMEOUT)
                .getFirst()
                .eventId();
    }

    private void recordDealCreated(Long dealId, Long storeId) {
        notificationOutboxService.recordDealCreated(dealId, storeId, LocalDateTime.now());
    }
}

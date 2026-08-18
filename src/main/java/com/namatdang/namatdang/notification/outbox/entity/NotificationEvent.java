package com.namatdang.namatdang.notification.outbox.entity;

import com.namatdang.namatdang.notification.event.NotificationEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_events_source_request",
                        columnNames = "source_request_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_events_deal",
                        columnList = "deal_id"
                ),
                @Index(
                        name = "idx_notification_events_reservation",
                        columnList = "reservation_id"
                ),
                @Index(
                        name = "idx_notification_events_publish_retry",
                        columnList = "status, next_retry_at, id"
                ),
                @Index(
                        name = "idx_notification_events_publish_recovery",
                        columnList = "status, publishing_started_at, id"
                )
        }
)
public class NotificationEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id")
    private Long dealId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "store_id")
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationEventType eventType;

    @Column(name = "source_request_key", nullable = false, length = 100)
    private String sourceRequestKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "publishing_started_at")
    private LocalDateTime publishingStartedAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public static NotificationEvent dealCreated(
            Long dealId,
            Long storeId,
            LocalDateTime dealCreatedAt
    ) {
        requirePositive(dealId, "dealId");
        requirePositive(storeId, "storeId");
        if (dealCreatedAt == null) {
            throw new IllegalArgumentException("dealCreatedAt은 필수입니다.");
        }

        NotificationEvent event = new NotificationEvent();
        event.dealId = dealId;
        event.storeId = storeId;
        event.eventType = NotificationEventType.DEAL_CREATED;
        event.sourceRequestKey = "DEAL:%d:CREATED".formatted(dealId);
        event.status = NotificationEventStatus.PENDING;
        event.retryCount = 0;
        event.occurredAt = dealCreatedAt;
        return event;
    }

    public void startPublishing(LocalDateTime startedAt) {
        if (status != NotificationEventStatus.PENDING
                && status != NotificationEventStatus.PUBLISHING) {
            throw new IllegalStateException("발행 가능한 알림 이벤트가 아닙니다.");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt은 필수입니다.");
        }

        status = NotificationEventStatus.PUBLISHING;
        publishingStartedAt = startedAt;
        nextRetryAt = null;
    }

    public void markPublished(LocalDateTime completedAt) {
        requirePublishing();
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 필수입니다.");
        }

        status = NotificationEventStatus.PUBLISHED;
        publishedAt = completedAt;
        publishingStartedAt = null;
        nextRetryAt = null;
        lastError = null;
    }

    public void recordFailure(
            String errorMessage,
            LocalDateTime retryAt,
            int maxAttemptCount
    ) {
        requirePublishing();
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage는 필수입니다.");
        }
        if (retryAt == null) {
            throw new IllegalArgumentException("retryAt은 필수입니다.");
        }
        if (maxAttemptCount <= 0) {
            throw new IllegalArgumentException("maxAttemptCount는 1 이상이어야 합니다.");
        }

        retryCount++;
        lastError = truncate(errorMessage.strip());
        publishingStartedAt = null;

        if (retryCount >= maxAttemptCount) {
            status = NotificationEventStatus.FAILED;
            nextRetryAt = null;
            return;
        }

        status = NotificationEventStatus.PENDING;
        nextRetryAt = retryAt;
    }

    private void requirePublishing() {
        if (status != NotificationEventStatus.PUBLISHING) {
            throw new IllegalStateException("발행 중인 알림 이벤트가 아닙니다.");
        }
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }
}

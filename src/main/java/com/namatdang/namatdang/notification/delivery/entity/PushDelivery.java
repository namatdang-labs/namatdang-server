package com.namatdang.namatdang.notification.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
        name = "push_deliveries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_push_deliveries_notification_registration",
                        columnNames = {"notification_id", "fcm_registration_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_push_deliveries_registration",
                        columnList = "fcm_registration_id"
                ),
                @Index(
                        name = "idx_push_deliveries_retry",
                        columnList = "status, next_retry_at, id"
                ),
                @Index(
                        name = "idx_push_deliveries_recovery",
                        columnList = "status, sending_started_at, id"
                )
        }
)
public class PushDelivery {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "fcm_registration_id", nullable = false)
    private Long fcmRegistrationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private PushDeliveryStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "sending_started_at")
    private LocalDateTime sendingStartedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PushDelivery pending(Long notificationId, Long fcmRegistrationId) {
        requirePositive(notificationId, "notificationId");
        requirePositive(fcmRegistrationId, "fcmRegistrationId");

        PushDelivery delivery = new PushDelivery();
        delivery.notificationId = notificationId;
        delivery.fcmRegistrationId = fcmRegistrationId;
        delivery.status = PushDeliveryStatus.PENDING;
        return delivery;
    }

    public void startSending(LocalDateTime startedAt) {
        if (status != PushDeliveryStatus.PENDING && status != PushDeliveryStatus.SENDING) {
            throw new IllegalStateException("발송 가능한 Push Delivery가 아닙니다.");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt은 필수입니다.");
        }
        status = PushDeliveryStatus.SENDING;
        sendingStartedAt = startedAt;
        nextRetryAt = null;
    }

    public void markSucceeded(LocalDateTime completedAt) {
        requireSending();
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 필수입니다.");
        }
        status = PushDeliveryStatus.SUCCEEDED;
        sentAt = completedAt;
        sendingStartedAt = null;
        nextRetryAt = null;
        lastError = null;
    }

    public void recordRetryableFailure(
            String errorMessage,
            LocalDateTime retryAt,
            int maxAttemptCount
    ) {
        requireSending();
        validateFailure(errorMessage, maxAttemptCount);
        if (retryAt == null) {
            throw new IllegalArgumentException("retryAt은 필수입니다.");
        }

        retryCount++;
        lastError = truncate(errorMessage.strip());
        sendingStartedAt = null;
        if (retryCount >= maxAttemptCount) {
            status = PushDeliveryStatus.FAILED;
            nextRetryAt = null;
            return;
        }
        status = PushDeliveryStatus.PENDING;
        nextRetryAt = retryAt;
    }

    public void markFailed(String errorMessage) {
        finishFailure(PushDeliveryStatus.FAILED, errorMessage);
    }

    public void markPermissionDenied(String errorMessage) {
        finishFailure(PushDeliveryStatus.PERMISSION_DENIED, errorMessage);
    }

    public void markInvalidToken(String errorMessage) {
        finishFailure(PushDeliveryStatus.INVALID_TOKEN, errorMessage);
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private void finishFailure(PushDeliveryStatus failureStatus, String errorMessage) {
        requireSending();
        validateFailure(errorMessage, 1);
        retryCount++;
        status = failureStatus;
        lastError = truncate(errorMessage.strip());
        sendingStartedAt = null;
        nextRetryAt = null;
    }

    private void requireSending() {
        if (status != PushDeliveryStatus.SENDING) {
            throw new IllegalStateException("발송 중인 Push Delivery가 아닙니다.");
        }
    }

    private static void validateFailure(String errorMessage, int maxAttemptCount) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage는 필수입니다.");
        }
        if (maxAttemptCount <= 0) {
            throw new IllegalArgumentException("maxAttemptCount는 1 이상이어야 합니다.");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}

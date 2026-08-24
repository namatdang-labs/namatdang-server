package com.namatdang.namatdang.notification.handler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification_event_consumptions",
        indexes = {
                @Index(
                        name = "idx_notification_event_consumptions_processed",
                        columnList = "processed_at"
                )
        }
)
public class NotificationEventConsumption {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public NotificationEventConsumption(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("eventId는 양수여야 합니다.");
        }
        this.eventId = eventId;
    }

    @PrePersist
    void prePersist() {
        processedAt = LocalDateTime.now();
    }
}

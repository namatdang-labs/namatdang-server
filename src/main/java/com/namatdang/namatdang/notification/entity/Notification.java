package com.namatdang.namatdang.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notifications_event_recipient",
                        columnNames = {"event_id", "recipient_user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_notifications_recipient_created",
                        columnList = "recipient_user_id, created_at"
                ),
                @Index(
                        name = "idx_notifications_recipient_read",
                        columnList = "recipient_user_id, is_read"
                )
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(
            Long eventId,
            Long recipientUserId,
            NotificationType notificationType,
            String title,
            String body,
            String linkUrl
    ) {
        this.eventId = eventId;
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.title = title;
        this.body = body;
        this.linkUrl = linkUrl;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void markAsRead() {
        if (read) {
            return;
        }

        this.read = true;
        this.readAt = LocalDateTime.now();
    }
}

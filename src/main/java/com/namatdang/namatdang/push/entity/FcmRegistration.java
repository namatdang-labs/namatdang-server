package com.namatdang.namatdang.push.entity;

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
        name = "fcm_registrations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fcm_registrations_token",
                        columnNames = "registration_token"
                )
        },
        indexes = {
                @Index(
                        name = "idx_fcm_registrations_user",
                        columnList = "user_id"
                )
        }
)
public class FcmRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "registration_token", nullable = false, length = 512)
    private String registrationToken;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "device_type", nullable = false, length = 20)
    private PushDeviceType deviceType;

    @Column(nullable = false, length = 30)
    private String browser;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FcmRegistration(
            Long userId,
            String registrationToken,
            PushDeviceType deviceType,
            String browser
    ) {
        this.userId = userId;
        this.registrationToken = registrationToken;
        this.deviceType = deviceType;
        this.browser = browser;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.lastRegisteredAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void register(Long userId, PushDeviceType deviceType, String browser) {
        this.userId = userId;
        this.deviceType = deviceType;
        this.browser = browser;
        this.lastRegisteredAt = LocalDateTime.now();
    }
}

package com.namatdang.namatdang.push.entity;

import com.namatdang.namatdang.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_fcm_registrations_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

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
            User user,
            String registrationToken,
            PushDeviceType deviceType,
            String browser
    ) {
        this.user = user;
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

    public void register(User user, PushDeviceType deviceType, String browser) {
        this.user = user;
        this.deviceType = deviceType;
        this.browser = browser;
        this.lastRegisteredAt = LocalDateTime.now();
    }

    public Long getUserId() {
        return user.getId();
    }
}

package com.namatdang.namatdang.store.entity;

import com.namatdang.namatdang.store.dto.StoreUpdateRequestDto;
import com.namatdang.namatdang.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "stores",
        indexes = @Index(name = "idx_stores_owner_id", columnList = "owner_id")
)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String address;

    private String addressDetail;

    @Column(length = 20)
    private String phoneNumber;

    @Lob
    private String description;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Store(
            User owner,
            String name,
            String address,
            String addressDetail,
            String phoneNumber,
            String description,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.owner = owner;
        this.name = name;
        this.address = address;
        this.addressDetail = addressDetail;
        this.phoneNumber = phoneNumber;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(StoreUpdateRequestDto requestDto) {
        if (requestDto.getName() != null) {
            this.name = requestDto.getName().strip();
        }
        if (requestDto.getAddress() != null) {
            this.address = requestDto.getAddress().strip();
        }
        if (requestDto.getAddressDetail() != null) {
            this.addressDetail = requestDto.getAddressDetail().strip();
        }
        if (requestDto.getPhoneNumber() != null) {
            this.phoneNumber = requestDto.getPhoneNumber().strip();
        }
        if (requestDto.getDescription() != null) {
            this.description = requestDto.getDescription().strip();
        }
        if (requestDto.getLatitude() != null) {
            this.latitude = requestDto.getLatitude();
        }
        if (requestDto.getLongitude() != null) {
            this.longitude = requestDto.getLongitude();
        }
    }
}

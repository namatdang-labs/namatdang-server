package com.namatdang.namatdang.notification.event;

import java.time.LocalDateTime;

public record DealCreatedEvent(
        Long dealId,
        Long storeId,
        LocalDateTime occurredAt
) {

    public DealCreatedEvent {
        requirePositive(dealId, "dealId");
        requirePositive(storeId, "storeId");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 필수입니다.");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }
}

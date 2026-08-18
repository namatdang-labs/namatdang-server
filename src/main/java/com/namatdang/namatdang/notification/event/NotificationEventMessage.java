package com.namatdang.namatdang.notification.event;

import java.time.LocalDateTime;

public record NotificationEventMessage(
        int schemaVersion,
        Long eventId,
        NotificationEventType eventType,
        Long dealId,
        Long storeId,
        LocalDateTime occurredAt
) {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    public static NotificationEventMessage create(
            Long eventId,
            NotificationEventType eventType,
            Long dealId,
            Long storeId,
            LocalDateTime occurredAt
    ) {
        return new NotificationEventMessage(
                CURRENT_SCHEMA_VERSION,
                eventId,
                eventType,
                dealId,
                storeId,
                occurredAt
        );
    }
}

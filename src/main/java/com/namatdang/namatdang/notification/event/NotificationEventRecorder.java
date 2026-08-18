package com.namatdang.namatdang.notification.event;

import java.time.LocalDateTime;

public interface NotificationEventRecorder {

    void recordDealCreated(
            Long dealId,
            Long storeId,
            LocalDateTime dealCreatedAt
    );
}

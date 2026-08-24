package com.namatdang.namatdang.notification.event;

import java.time.LocalDateTime;

public interface NotificationEventRecorder {

    void recordDealCreated(
            Long dealId,
            Long storeId,
            LocalDateTime dealCreatedAt
    );

    void recordReservationConfirmed(
            Long reservationId,
            Long storeId,
            LocalDateTime reservationCreatedAt
    );

    void recordReservationCanceled(
            Long reservationId,
            Long storeId,
            LocalDateTime reservationCanceledAt
    );
}

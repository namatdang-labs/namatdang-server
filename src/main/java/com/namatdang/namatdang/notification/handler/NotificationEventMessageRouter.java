package com.namatdang.namatdang.notification.handler;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;

public class NotificationEventMessageRouter implements NotificationEventMessageHandler {

    private final DealCreatedNotificationHandler dealCreatedHandler;
    private final ReservationNotificationHandler reservationHandler;

    public NotificationEventMessageRouter(
            DealCreatedNotificationHandler dealCreatedHandler,
            ReservationNotificationHandler reservationHandler
    ) {
        this.dealCreatedHandler = dealCreatedHandler;
        this.reservationHandler = reservationHandler;
    }

    @Override
    public void handle(NotificationEventMessage message) {
        if (message == null || message.eventType() == null) {
            throw new IllegalArgumentException("이벤트 유형은 필수입니다.");
        }

        switch (message.eventType()) {
            case DEAL_CREATED -> dealCreatedHandler.handle(message);
            case RESERVATION_CONFIRMED, RESERVATION_CANCELED -> reservationHandler.handle(message);
        }
    }
}

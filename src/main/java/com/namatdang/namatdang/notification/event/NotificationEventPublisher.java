package com.namatdang.namatdang.notification.event;

public interface NotificationEventPublisher {

    void publish(NotificationEventMessage message);
}

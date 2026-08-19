package com.namatdang.namatdang.notification.handler;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;

public interface NotificationEventMessageHandler {

    void handle(NotificationEventMessage message);
}

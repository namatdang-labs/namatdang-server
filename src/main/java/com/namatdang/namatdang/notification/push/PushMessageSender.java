package com.namatdang.namatdang.notification.push;

public interface PushMessageSender {

    PushSendResult send(PushTarget target, PushMessage message);
}

package com.namatdang.namatdang.notification.messaging.sqs;

public class NotificationSqsException extends RuntimeException {

    public NotificationSqsException(String message, Throwable cause) {
        super(message, cause);
    }
}

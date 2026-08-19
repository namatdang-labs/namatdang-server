package com.namatdang.namatdang.notification.push;

public enum PushSendResultType {

    SUCCEEDED,
    INVALID_TOKEN,
    PERMISSION_DENIED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}

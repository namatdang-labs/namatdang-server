package com.namatdang.namatdang.notification.push;

public record PushSendResult(
        PushSendResultType type,
        String errorMessage
) {

    public PushSendResult {
        if (type == null) {
            throw new IllegalArgumentException("type은 필수입니다.");
        }
        if (type != PushSendResultType.SUCCEEDED
                && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("실패 결과에는 errorMessage가 필요합니다.");
        }
    }

    public static PushSendResult succeeded() {
        return new PushSendResult(PushSendResultType.SUCCEEDED, null);
    }

    public static PushSendResult invalidToken(String errorMessage) {
        return new PushSendResult(PushSendResultType.INVALID_TOKEN, errorMessage);
    }

    public static PushSendResult permissionDenied(String errorMessage) {
        return new PushSendResult(PushSendResultType.PERMISSION_DENIED, errorMessage);
    }

    public static PushSendResult retryableFailure(String errorMessage) {
        return new PushSendResult(PushSendResultType.RETRYABLE_FAILURE, errorMessage);
    }

    public static PushSendResult permanentFailure(String errorMessage) {
        return new PushSendResult(PushSendResultType.PERMANENT_FAILURE, errorMessage);
    }
}

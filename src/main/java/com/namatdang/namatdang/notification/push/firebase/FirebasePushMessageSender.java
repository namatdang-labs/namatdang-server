package com.namatdang.namatdang.notification.push.firebase;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import com.namatdang.namatdang.notification.push.PushMessage;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import com.namatdang.namatdang.notification.push.PushSendResult;
import com.namatdang.namatdang.notification.push.PushTarget;

public final class FirebasePushMessageSender implements PushMessageSender {

    private final FirebaseMessageClient firebaseMessageClient;

    FirebasePushMessageSender(FirebaseMessageClient firebaseMessageClient) {
        this.firebaseMessageClient = firebaseMessageClient;
    }

    @Override
    public PushSendResult send(PushTarget target, PushMessage message) {
        FirebasePushRequest request = new FirebasePushRequest(
                target.registrationToken(),
                message.title(),
                message.body(),
                message.linkUrl()
        );

        FirebaseSendAttempt attempt;
        try {
            attempt = firebaseMessageClient.send(request);
        } catch (RuntimeException exception) {
            return PushSendResult.retryableFailure("FCM_UNKNOWN_ERROR");
        }
        if (attempt.succeeded()) {
            return PushSendResult.succeeded();
        }
        return classify(attempt.messagingErrorCode(), attempt.errorCode());
    }

    private PushSendResult classify(
            MessagingErrorCode messagingErrorCode,
            ErrorCode errorCode
    ) {
        if (messagingErrorCode != null) {
            return switch (messagingErrorCode) {
                case UNREGISTERED -> PushSendResult.invalidToken("FCM_UNREGISTERED");
                case SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR ->
                        PushSendResult.permissionDenied("FCM_" + messagingErrorCode.name());
                case QUOTA_EXCEEDED, UNAVAILABLE, INTERNAL ->
                        PushSendResult.retryableFailure("FCM_" + messagingErrorCode.name());
                case INVALID_ARGUMENT ->
                        PushSendResult.permanentFailure("FCM_INVALID_ARGUMENT");
            };
        }
        if (errorCode == null) {
            return PushSendResult.retryableFailure("FCM_UNKNOWN_ERROR");
        }
        return switch (errorCode) {
            case PERMISSION_DENIED, UNAUTHENTICATED ->
                    PushSendResult.permissionDenied("FIREBASE_" + errorCode.name());
            case RESOURCE_EXHAUSTED, UNAVAILABLE, INTERNAL, DEADLINE_EXCEEDED,
                    ABORTED, CANCELLED, UNKNOWN ->
                    PushSendResult.retryableFailure("FIREBASE_" + errorCode.name());
            default -> PushSendResult.permanentFailure("FIREBASE_" + errorCode.name());
        };
    }
}

@FunctionalInterface
interface FirebaseMessageClient {

    FirebaseSendAttempt send(FirebasePushRequest request);
}

record FirebasePushRequest(
        String registrationToken,
        String title,
        String body,
        String linkUrl
) {
}

record FirebaseSendAttempt(
        boolean succeeded,
        MessagingErrorCode messagingErrorCode,
        ErrorCode errorCode
) {

    static FirebaseSendAttempt success() {
        return new FirebaseSendAttempt(true, null, null);
    }

    static FirebaseSendAttempt failed(
            MessagingErrorCode messagingErrorCode,
            ErrorCode errorCode
    ) {
        return new FirebaseSendAttempt(false, messagingErrorCode, errorCode);
    }
}

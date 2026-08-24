package com.namatdang.namatdang.notification.push.firebase;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;

final class FirebaseAdminMessageClient implements FirebaseMessageClient {

    private final FirebaseMessaging firebaseMessaging;

    FirebaseAdminMessageClient(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public FirebaseSendAttempt send(FirebasePushRequest request) {
        try {
            firebaseMessaging.send(toFirebaseMessage(request));
            return FirebaseSendAttempt.success();
        } catch (FirebaseMessagingException exception) {
            return FirebaseSendAttempt.failed(
                    exception.getMessagingErrorCode(),
                    exception.getErrorCode()
            );
        } catch (IllegalArgumentException exception) {
            return FirebaseSendAttempt.failed(
                    MessagingErrorCode.INVALID_ARGUMENT,
                    ErrorCode.INVALID_ARGUMENT
            );
        }
    }

    @SuppressWarnings("deprecation")
    Message toFirebaseMessage(FirebasePushRequest request) {
        WebpushConfig.Builder webpush = WebpushConfig.builder()
                .setNotification(WebpushNotification.builder()
                        .setTitle(request.title())
                        .setBody(request.body())
                        .build());
        if (request.linkUrl() != null && !request.linkUrl().isBlank()) {
            webpush.putData("linkUrl", request.linkUrl());
        }

        return Message.builder()
                .setToken(request.registrationToken())
                .setWebpushConfig(webpush.build())
                .build();
    }
}

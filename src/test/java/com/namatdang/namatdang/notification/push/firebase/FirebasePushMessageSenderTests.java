package com.namatdang.namatdang.notification.push.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import com.namatdang.namatdang.notification.push.PushMessage;
import com.namatdang.namatdang.notification.push.PushSendResult;
import com.namatdang.namatdang.notification.push.PushSendResultType;
import com.namatdang.namatdang.notification.push.PushTarget;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebasePushMessageSenderTests {

    private static final String REGISTRATION_TOKEN = "test-registration-token";
    private static final PushTarget TARGET = new PushTarget(1L, 2L, REGISTRATION_TOKEN);
    private static final PushMessage MESSAGE = new PushMessage(
            "새로운 마감 할인",
            "즐겨찾기한 매장에 Deal이 등록됐어요.",
            "/deals/10"
    );

    @Test
    void returnsSucceededAndMapsPayload() {
        StubFirebaseMessageClient client = new StubFirebaseMessageClient(
                FirebaseSendAttempt.success()
        );

        PushSendResult result = new FirebasePushMessageSender(client).send(TARGET, MESSAGE);

        assertThat(result.type()).isEqualTo(PushSendResultType.SUCCEEDED);
        assertThat(client.request).isEqualTo(new FirebasePushRequest(
                REGISTRATION_TOKEN,
                MESSAGE.title(),
                MESSAGE.body(),
                MESSAGE.linkUrl()
        ));
    }

    @Test
    void buildsFirebaseWebPushPayloadWithTitleBodyAndLinkUrl() throws Exception {
        FirebasePushRequest request = new FirebasePushRequest(
                REGISTRATION_TOKEN,
                MESSAGE.title(),
                MESSAGE.body(),
                MESSAGE.linkUrl()
        );

        Message firebaseMessage = new FirebaseAdminMessageClient(null)
                .toFirebaseMessage(request);
        WebpushConfig webpush = (WebpushConfig) readField(firebaseMessage, "webpushConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> notification = (Map<String, Object>) readField(
                webpush,
                "notification"
        );
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) readField(webpush, "data");

        assertThat(readField(firebaseMessage, "token")).isEqualTo(REGISTRATION_TOKEN);
        assertThat(notification)
                .containsEntry("title", MESSAGE.title())
                .containsEntry("body", MESSAGE.body());
        assertThat(data).containsEntry("linkUrl", MESSAGE.linkUrl());
    }

    @Test
    void mapsUnregisteredToInvalidToken() {
        PushSendResult result = sendFailure(MessagingErrorCode.UNREGISTERED, ErrorCode.NOT_FOUND);

        assertThat(result.type()).isEqualTo(PushSendResultType.INVALID_TOKEN);
        assertThat(result.errorMessage()).isEqualTo("FCM_UNREGISTERED");
    }

    @Test
    void mapsAuthenticationAndPermissionErrorsToPermissionDenied() {
        assertThat(sendFailure(
                MessagingErrorCode.SENDER_ID_MISMATCH,
                ErrorCode.PERMISSION_DENIED
        ).type()).isEqualTo(PushSendResultType.PERMISSION_DENIED);
        assertThat(sendFailure(null, ErrorCode.UNAUTHENTICATED).type())
                .isEqualTo(PushSendResultType.PERMISSION_DENIED);
    }

    @Test
    void mapsTemporaryErrorsToRetryableFailure() {
        assertThat(sendFailure(
                MessagingErrorCode.QUOTA_EXCEEDED,
                ErrorCode.RESOURCE_EXHAUSTED
        ).type()).isEqualTo(PushSendResultType.RETRYABLE_FAILURE);
        assertThat(sendFailure(null, ErrorCode.DEADLINE_EXCEEDED).type())
                .isEqualTo(PushSendResultType.RETRYABLE_FAILURE);
    }

    @Test
    void mapsInvalidRequestToPermanentFailure() {
        PushSendResult result = sendFailure(
                MessagingErrorCode.INVALID_ARGUMENT,
                ErrorCode.INVALID_ARGUMENT
        );

        assertThat(result.type()).isEqualTo(PushSendResultType.PERMANENT_FAILURE);
    }

    @Test
    void doesNotExposeRegistrationTokenInFailureResult() {
        StubFirebaseMessageClient client = new StubFirebaseMessageClient(null);
        client.runtimeException = new IllegalStateException(REGISTRATION_TOKEN);

        PushSendResult result = new FirebasePushMessageSender(client).send(TARGET, MESSAGE);

        assertThat(result.type()).isEqualTo(PushSendResultType.RETRYABLE_FAILURE);
        assertThat(result.errorMessage()).doesNotContain(REGISTRATION_TOKEN);
    }

    private PushSendResult sendFailure(
            MessagingErrorCode messagingErrorCode,
            ErrorCode errorCode
    ) {
        StubFirebaseMessageClient client = new StubFirebaseMessageClient(
                FirebaseSendAttempt.failed(messagingErrorCode, errorCode)
        );
        return new FirebasePushMessageSender(client).send(TARGET, MESSAGE);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class StubFirebaseMessageClient implements FirebaseMessageClient {

        private final FirebaseSendAttempt attempt;
        private FirebasePushRequest request;
        private RuntimeException runtimeException;

        private StubFirebaseMessageClient(FirebaseSendAttempt attempt) {
            this.attempt = attempt;
        }

        @Override
        public FirebaseSendAttempt send(FirebasePushRequest request) {
            this.request = request;
            if (runtimeException != null) {
                throw runtimeException;
            }
            return attempt;
        }
    }
}

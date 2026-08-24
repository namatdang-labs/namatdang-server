package com.namatdang.namatdang.notification.push.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.namatdang.namatdang.notification.push.PushMessage;
import com.namatdang.namatdang.notification.push.PushSendResult;
import com.namatdang.namatdang.notification.push.PushSendResultType;
import com.namatdang.namatdang.notification.push.PushTarget;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RUN_FIREBASE_TESTS", matches = "true")
class FirebasePushLiveIntegrationTests {

    @Test
    void sendsToRegistrationTokenWithApplicationDefaultCredentials() throws IOException {
        String projectId = requiredEnvironmentVariable("FIREBASE_PROJECT_ID");
        String registrationToken = requiredEnvironmentVariable(
                "FIREBASE_TEST_REGISTRATION_TOKEN"
        );
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(
                options,
                "namatdang-live-test"
        );

        try {
            FirebasePushMessageSender sender = new FirebasePushMessageSender(
                    new FirebaseAdminMessageClient(FirebaseMessaging.getInstance(firebaseApp))
            );

            PushSendResult result = sender.send(
                    new PushTarget(1L, 1L, registrationToken),
                    new PushMessage(
                            "남았당 Firebase 연동 테스트",
                            "브라우저에서 이 알림을 수신했는지 확인해 주세요.",
                            "/"
                    )
            );

            assertThat(result.type()).isEqualTo(PushSendResultType.SUCCEEDED);
        } finally {
            firebaseApp.delete();
        }
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}

package com.namatdang.namatdang.notification.push.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "notification.push.enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(FirebasePushProperties.class)
public class FirebasePushConfiguration {

    private static final String FIREBASE_APP_NAME = "namatdang-notification-push";

    @Bean(destroyMethod = "delete")
    FirebaseApp notificationFirebaseApp(FirebasePushProperties properties) throws IOException {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(properties.requiredProjectId())
                .build();
        return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
    }

    @Bean
    FirebaseMessaging notificationFirebaseMessaging(FirebaseApp notificationFirebaseApp) {
        return FirebaseMessaging.getInstance(notificationFirebaseApp);
    }

    @Bean
    FirebaseMessageClient firebaseMessageClient(FirebaseMessaging notificationFirebaseMessaging) {
        return new FirebaseAdminMessageClient(notificationFirebaseMessaging);
    }

    @Bean
    PushMessageSender firebasePushMessageSender(FirebaseMessageClient firebaseMessageClient) {
        return new FirebasePushMessageSender(firebaseMessageClient);
    }
}

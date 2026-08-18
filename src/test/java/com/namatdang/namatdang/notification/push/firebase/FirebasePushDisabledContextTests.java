package com.namatdang.namatdang.notification.push.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "notification.push.enabled=false",
        "notification.push.firebase.project-id="
})
class FirebasePushDisabledContextTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsWithoutFirebaseCredentialsWhenPushIsDisabled() {
        assertThat(applicationContext.getBeansOfType(FirebaseApp.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(FirebaseMessaging.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PushMessageSender.class)).isEmpty();
    }
}

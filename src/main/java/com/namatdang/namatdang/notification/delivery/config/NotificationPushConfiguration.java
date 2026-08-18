package com.namatdang.namatdang.notification.delivery.config;

import com.namatdang.namatdang.notification.delivery.service.PushDeliveryProcessor;
import com.namatdang.namatdang.notification.delivery.service.PushDeliveryService;
import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.push.InvalidPushRegistrationHandler;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        name = "notification.push.enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(NotificationPushProperties.class)
public class NotificationPushConfiguration {

    @Bean
    PushDeliveryProcessor pushDeliveryProcessor(
            PushDeliveryService pushDeliveryService,
            ActivePushRegistrationReader pushRegistrationReader,
            PushMessageSender pushMessageSender,
            InvalidPushRegistrationHandler invalidPushRegistrationHandler,
            NotificationPushProperties properties
    ) {
        properties.validate();
        return new PushDeliveryProcessor(
                pushDeliveryService,
                pushRegistrationReader,
                pushMessageSender,
                invalidPushRegistrationHandler
        );
    }
}

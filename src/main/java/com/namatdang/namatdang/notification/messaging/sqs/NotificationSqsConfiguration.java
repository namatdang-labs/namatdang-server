package com.namatdang.namatdang.notification.messaging.sqs;

import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.handler.DealCreatedNotificationHandler;
import com.namatdang.namatdang.notification.handler.NotificationEventMessageRouter;
import com.namatdang.namatdang.notification.handler.ReservationNotificationHandler;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import com.namatdang.namatdang.notification.recipient.ReservationRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(
        name = "notification.messaging.sqs.enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(NotificationSqsProperties.class)
public class NotificationSqsConfiguration {

    static final String SQS_POLLING_TASK_SCHEDULER = "sqsPollingTaskScheduler";

    @Bean
    SqsClient notificationSqsClient(NotificationSqsProperties properties) {
        properties.validate();
        var builder = SqsClient.builder()
                .region(Region.of(properties.getRegion()))
                .httpClientBuilder(UrlConnectionHttpClient.builder());

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.builder()
                                    .accessKeyId("localstack")
                                    .secretAccessKey("localstack")
                                    .build()
                    ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Bean
    SqsNotificationEventPublisher sqsNotificationEventPublisher(
            SqsClient notificationSqsClient,
            ObjectMapper objectMapper,
            NotificationSqsProperties properties
    ) {
        return new SqsNotificationEventPublisher(
                notificationSqsClient,
                objectMapper,
                properties.getQueueUrl()
        );
    }

    @Configuration
    @ConditionalOnProperty(
            name = "notification.messaging.sqs.consumer-enabled",
            havingValue = "true"
    )
    static class ConsumerConfiguration {

        @Bean(name = SQS_POLLING_TASK_SCHEDULER)
        ThreadPoolTaskScheduler sqsPollingTaskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("sqs-notification-poller-");
            scheduler.setWaitForTasksToCompleteOnShutdown(true);
            scheduler.setAwaitTerminationSeconds(25);
            return scheduler;
        }

        @Bean
        DealCreatedNotificationHandler dealCreatedNotificationHandler(
                FavoriteRecipientReader favoriteRecipientReader,
                ActivePushRegistrationReader pushRegistrationReader,
                NotificationRepository notificationRepository,
                PushDeliveryRepository pushDeliveryRepository,
                NotificationEventConsumptionRepository consumptionRepository
        ) {
            return new DealCreatedNotificationHandler(
                    favoriteRecipientReader,
                    pushRegistrationReader,
                    notificationRepository,
                    pushDeliveryRepository,
                    consumptionRepository
            );
        }

        @Bean
        ReservationNotificationHandler reservationNotificationHandler(
                ReservationRecipientReader reservationRecipientReader,
                ActivePushRegistrationReader pushRegistrationReader,
                NotificationRepository notificationRepository,
                PushDeliveryRepository pushDeliveryRepository,
                NotificationEventConsumptionRepository consumptionRepository
        ) {
            return new ReservationNotificationHandler(
                    reservationRecipientReader,
                    pushRegistrationReader,
                    notificationRepository,
                    pushDeliveryRepository,
                    consumptionRepository
            );
        }

        @Bean
        NotificationEventMessageRouter notificationEventMessageRouter(
                DealCreatedNotificationHandler dealCreatedNotificationHandler,
                ReservationNotificationHandler reservationNotificationHandler
        ) {
            return new NotificationEventMessageRouter(
                    dealCreatedNotificationHandler,
                    reservationNotificationHandler
            );
        }

        @Bean
        SqsNotificationEventConsumer sqsNotificationEventConsumer(
                SqsClient notificationSqsClient,
                ObjectMapper objectMapper,
                NotificationEventMessageRouter messageHandler,
                NotificationSqsProperties properties
        ) {
            return new SqsNotificationEventConsumer(
                    notificationSqsClient,
                    objectMapper,
                    messageHandler,
                    properties.getQueueUrl(),
                    properties.getReceiveBatchSize(),
                    properties.getReceiveWaitTimeSeconds()
            );
        }
    }
}

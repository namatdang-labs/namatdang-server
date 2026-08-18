package com.namatdang.namatdang.notification.messaging.sqs;

import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.dealCreatedMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_LOCALSTACK_TESTS", matches = "true")
class SqsLocalStackIntegrationTests {

    private static final String QUEUE_NAME = "namatdang-notification-events";

    @Autowired
    private ObjectMapper objectMapper;

    private SqsClient sqsClient;
    private String queueUrl;

    @BeforeEach
    void setUp() {
        sqsClient = SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.builder()
                                .accessKeyId("localstack")
                                .secretAccessKey("localstack")
                                .build()
                ))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        queueUrl = sqsClient.getQueueUrl(request -> request.queueName(QUEUE_NAME)).queueUrl();
        sqsClient.purgeQueue(request -> request.queueUrl(queueUrl));
    }

    @AfterEach
    void tearDown() {
        sqsClient.close();
    }

    @Test
    void publishConsumeAndAcknowledgeThroughLocalStack() {
        SqsNotificationEventPublisher publisher = new SqsNotificationEventPublisher(
                sqsClient,
                objectMapper,
                queueUrl
        );
        AtomicReference<NotificationEventMessage> consumedMessage = new AtomicReference<>();
        SqsNotificationEventConsumer consumer = new SqsNotificationEventConsumer(
                sqsClient,
                objectMapper,
                consumedMessage::set,
                queueUrl,
                10
        );

        publisher.publish(dealCreatedMessage());
        assertThat(consumer.pollOnce()).isEqualTo(1);

        assertThat(consumedMessage.get()).isEqualTo(dealCreatedMessage());
        assertThat(consumer.pollOnce()).isZero();
    }

    @Test
    void mainQueueHasDlqRedrivePolicy() {
        String redrivePolicy = sqsClient.getQueueAttributes(request -> request
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.REDRIVE_POLICY))
                .attributes()
                .get(QueueAttributeName.REDRIVE_POLICY);

        assertThat(redrivePolicy)
                .contains("namatdang-notification-events-dlq")
                .contains("\"maxReceiveCount\":\"3\"");
    }
}

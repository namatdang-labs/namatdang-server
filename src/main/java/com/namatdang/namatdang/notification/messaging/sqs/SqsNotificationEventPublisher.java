package com.namatdang.namatdang.notification.messaging.sqs;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventPublisher;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

public class SqsNotificationEventPublisher implements NotificationEventPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsNotificationEventPublisher(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            String queueUrl
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = requireQueueUrl(queueUrl);
    }

    @Override
    public void publish(NotificationEventMessage message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build());
        } catch (Exception exception) {
            throw new NotificationSqsException("SQS 알림 이벤트 발행에 실패했습니다.", exception);
        }
    }

    private static String requireQueueUrl(String queueUrl) {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalArgumentException("queueUrl은 필수입니다.");
        }
        return queueUrl;
    }
}

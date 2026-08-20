package com.namatdang.namatdang.notification.messaging.sqs;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.handler.NotificationEventMessageHandler;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class SqsNotificationEventConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final NotificationEventMessageHandler messageHandler;
    private final String queueUrl;
    private final int receiveBatchSize;
    private final int receiveWaitTimeSeconds;

    public SqsNotificationEventConsumer(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            NotificationEventMessageHandler messageHandler,
            String queueUrl,
            int receiveBatchSize,
            int receiveWaitTimeSeconds
    ) {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalArgumentException("queueUrl은 필수입니다.");
        }
        if (receiveBatchSize <= 0 || receiveBatchSize > 10) {
            throw new IllegalArgumentException("receiveBatchSize는 1 이상 10 이하여야 합니다.");
        }
        if (receiveWaitTimeSeconds < 0 || receiveWaitTimeSeconds > 20) {
            throw new IllegalArgumentException("receiveWaitTimeSeconds는 0 이상 20 이하여야 합니다.");
        }
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.messageHandler = messageHandler;
        this.queueUrl = queueUrl;
        this.receiveBatchSize = receiveBatchSize;
        this.receiveWaitTimeSeconds = receiveWaitTimeSeconds;
    }

    public int pollOnce() {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(receiveBatchSize)
                        .waitTimeSeconds(receiveWaitTimeSeconds)
                        .build())
                .messages();
        messages.forEach(this::handle);
        return messages.size();
    }

    private void handle(Message sqsMessage) {
        try {
            NotificationEventMessage message = objectMapper.readValue(
                    sqsMessage.body(),
                    NotificationEventMessage.class
            );
            messageHandler.handle(message);
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(sqsMessage.receiptHandle())
                    .build());
        } catch (Exception exception) {
            log.warn(
                    "SQS 알림 이벤트 처리 실패. messageId={}",
                    sqsMessage.messageId(),
                    exception
            );
        }
    }
}

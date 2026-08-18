package com.namatdang.namatdang.notification.messaging.sqs;

import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.dealCreatedMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class SqsNotificationAdapterTests {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/events";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializeAndPublishVersionedEventMessage() throws Exception {
        AtomicReference<SendMessageRequest> sentRequest = new AtomicReference<>();
        SqsClient sqsClient = proxyClient((methodName, arguments) -> {
            if (methodName.equals("sendMessage")) {
                sentRequest.set((SendMessageRequest) arguments[0]);
                return SendMessageResponse.builder().messageId("message-1").build();
            }
            return defaultResponse(methodName);
        });
        SqsNotificationEventPublisher publisher = new SqsNotificationEventPublisher(
                sqsClient,
                objectMapper,
                QUEUE_URL
        );

        publisher.publish(dealCreatedMessage());

        assertThat(sentRequest.get().queueUrl()).isEqualTo(QUEUE_URL);
        NotificationEventMessage restoredMessage = objectMapper.readValue(
                sentRequest.get().messageBody(),
                NotificationEventMessage.class
        );
        assertThat(restoredMessage).isEqualTo(dealCreatedMessage());
    }

    @Test
    void deleteSqsMessageOnlyAfterSuccessfulHandling() throws Exception {
        NotificationEventMessage expectedMessage = dealCreatedMessage();
        String body = objectMapper.writeValueAsString(expectedMessage);
        AtomicReference<DeleteMessageRequest> deletedRequest = new AtomicReference<>();
        SqsClient sqsClient = receivingClient(body, deletedRequest);
        AtomicReference<NotificationEventMessage> handledMessage = new AtomicReference<>();
        SqsNotificationEventConsumer consumer = new SqsNotificationEventConsumer(
                sqsClient,
                objectMapper,
                handledMessage::set,
                QUEUE_URL,
                10
        );

        assertThat(consumer.pollOnce()).isEqualTo(1);

        assertThat(handledMessage.get()).isEqualTo(expectedMessage);
        assertThat(deletedRequest.get().queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(deletedRequest.get().receiptHandle()).isEqualTo("receipt-1");
    }

    @Test
    void leaveFailedMessageForSqsRetryAndDlqRedrive() throws Exception {
        String body = objectMapper.writeValueAsString(dealCreatedMessage());
        AtomicReference<DeleteMessageRequest> deletedRequest = new AtomicReference<>();
        SqsClient sqsClient = receivingClient(body, deletedRequest);
        SqsNotificationEventConsumer consumer = new SqsNotificationEventConsumer(
                sqsClient,
                objectMapper,
                message -> {
                    throw new IllegalStateException("temporary database failure");
                },
                QUEUE_URL,
                10
        );

        assertThat(consumer.pollOnce()).isEqualTo(1);

        assertThat(deletedRequest.get()).isNull();
    }

    private SqsClient receivingClient(
            String body,
            AtomicReference<DeleteMessageRequest> deletedRequest
    ) {
        AtomicInteger receiveCount = new AtomicInteger();
        return proxyClient((methodName, arguments) -> {
            if (methodName.equals("receiveMessage")) {
                if (receiveCount.getAndIncrement() > 0) {
                    return ReceiveMessageResponse.builder().messages(List.of()).build();
                }
                return ReceiveMessageResponse.builder()
                        .messages(Message.builder()
                                .messageId("message-1")
                                .receiptHandle("receipt-1")
                                .body(body)
                                .build())
                        .build();
            }
            if (methodName.equals("deleteMessage")) {
                deletedRequest.set((DeleteMessageRequest) arguments[0]);
                return DeleteMessageResponse.builder().build();
            }
            return defaultResponse(methodName);
        });
    }

    private SqsClient proxyClient(SqsInvocation invocation) {
        return (SqsClient) Proxy.newProxyInstance(
                SqsClient.class.getClassLoader(),
                new Class<?>[]{SqsClient.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "SqsClientTestProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method.getName(), arguments);
                }
        );
    }

    private Object defaultResponse(String methodName) {
        return switch (methodName) {
            case "serviceName" -> "sqs";
            case "close" -> null;
            default -> throw new UnsupportedOperationException(methodName);
        };
    }

    @FunctionalInterface
    private interface SqsInvocation {

        Object invoke(String methodName, Object[] arguments);
    }
}

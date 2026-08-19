package com.namatdang.namatdang.notification.messaging.sqs;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.messaging.sqs")
public class NotificationSqsProperties {

    private boolean enabled;
    private boolean consumerEnabled;
    private String region = "ap-northeast-2";
    private String endpoint;
    private String queueUrl;
    private int publishBatchSize = 10;
    private Duration publishingTimeout = Duration.ofMinutes(5);
    private Duration publishRetryDelay = Duration.ofSeconds(10);
    private int publishMaxAttemptCount = 5;
    private int receiveBatchSize = 10;

    public void validate() {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("notification.messaging.sqs.region은 필수입니다.");
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("notification.messaging.sqs.queue-url은 필수입니다.");
        }
        if (publishBatchSize <= 0 || publishBatchSize > 100) {
            throw new IllegalStateException("publish-batch-size는 1 이상 100 이하여야 합니다.");
        }
        if (receiveBatchSize <= 0 || receiveBatchSize > 10) {
            throw new IllegalStateException("receive-batch-size는 1 이상 10 이하여야 합니다.");
        }
        if (publishMaxAttemptCount <= 0) {
            throw new IllegalStateException("publish-max-attempt-count는 1 이상이어야 합니다.");
        }
    }
}

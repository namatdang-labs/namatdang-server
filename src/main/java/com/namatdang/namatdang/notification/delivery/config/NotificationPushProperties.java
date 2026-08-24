package com.namatdang.namatdang.notification.delivery.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.push")
public class NotificationPushProperties {

    private boolean enabled;
    private int batchSize = 20;
    private Duration sendingTimeout = Duration.ofMinutes(5);
    private Duration retryDelay = Duration.ofSeconds(30);
    private int maxAttemptCount = 5;

    public void validate() {
        if (batchSize <= 0 || batchSize > 100) {
            throw new IllegalStateException("notification.push.batch-size는 1 이상 100 이하여야 합니다.");
        }
        if (sendingTimeout == null || sendingTimeout.isZero() || sendingTimeout.isNegative()) {
            throw new IllegalStateException("notification.push.sending-timeout은 양수여야 합니다.");
        }
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalStateException("notification.push.retry-delay는 0 이상이어야 합니다.");
        }
        if (maxAttemptCount <= 0) {
            throw new IllegalStateException("notification.push.max-attempt-count는 1 이상이어야 합니다.");
        }
    }
}

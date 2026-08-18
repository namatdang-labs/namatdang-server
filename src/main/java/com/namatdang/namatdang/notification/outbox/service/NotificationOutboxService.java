package com.namatdang.namatdang.notification.outbox.service;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventRecorder;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEvent;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEventStatus;
import com.namatdang.namatdang.notification.outbox.repository.NotificationEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService implements NotificationEventRecorder {

    private static final int MAX_BATCH_SIZE = 100;

    private final NotificationEventRepository notificationEventRepository;

    @Override
    @Transactional
    public void recordDealCreated(
            Long dealId,
            Long storeId,
            LocalDateTime dealCreatedAt
    ) {
        NotificationEvent event = NotificationEvent.dealCreated(dealId, storeId, dealCreatedAt);
        if (notificationEventRepository.existsBySourceRequestKey(event.getSourceRequestKey())) {
            return;
        }
        notificationEventRepository.save(event);
    }

    @Transactional
    public List<NotificationEventMessage> claimPublishableEvents(
            int batchSize,
            Duration publishingTimeout
    ) {
        validateBatchSize(batchSize);
        if (publishingTimeout == null || publishingTimeout.isNegative() || publishingTimeout.isZero()) {
            throw new IllegalArgumentException("publishingTimeout은 양수여야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minus(publishingTimeout);
        List<NotificationEvent> events = notificationEventRepository.findPublishableEventsForUpdate(
                NotificationEventStatus.PENDING,
                NotificationEventStatus.PUBLISHING,
                now,
                staleBefore,
                PageRequest.of(0, batchSize)
        );

        events.forEach(event -> event.startPublishing(now));
        return events.stream()
                .map(event -> NotificationEventMessage.create(
                        event.getId(),
                        event.getEventType(),
                        event.getDealId(),
                        event.getStoreId(),
                        event.getOccurredAt()
                ))
                .toList();
    }

    @Transactional
    public void markPublished(Long eventId) {
        NotificationEvent event = findByIdForUpdate(eventId);
        event.markPublished(LocalDateTime.now());
    }

    @Transactional
    public void recordPublishFailure(
            Long eventId,
            String errorMessage,
            Duration retryDelay,
            int maxAttemptCount
    ) {
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay는 0 이상이어야 합니다.");
        }

        NotificationEvent event = findByIdForUpdate(eventId);
        event.recordFailure(
                errorMessage,
                LocalDateTime.now().plus(retryDelay),
                maxAttemptCount
        );
    }

    private NotificationEvent findByIdForUpdate(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("eventId는 양수여야 합니다.");
        }
        return notificationEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("알림 이벤트를 찾을 수 없습니다."));
    }

    private static void validateBatchSize(int batchSize) {
        if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize는 1 이상 100 이하여야 합니다.");
        }
    }
}

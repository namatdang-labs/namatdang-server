package com.namatdang.namatdang.notification.delivery.service;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.entity.PushDeliveryStatus;
import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.push.PushMessage;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private static final int MAX_BATCH_SIZE = 100;

    private final PushDeliveryRepository pushDeliveryRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public List<PushDeliveryTask> claimSendableDeliveries(
            int batchSize,
            Duration sendingTimeout
    ) {
        validateBatchSize(batchSize);
        if (sendingTimeout == null || sendingTimeout.isZero() || sendingTimeout.isNegative()) {
            throw new IllegalArgumentException("sendingTimeout은 양수여야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<PushDelivery> deliveries = pushDeliveryRepository.findSendableDeliveriesForUpdate(
                PushDeliveryStatus.PENDING,
                PushDeliveryStatus.SENDING,
                now,
                now.minus(sendingTimeout),
                PageRequest.of(0, batchSize)
        );
        deliveries.forEach(delivery -> delivery.startSending(now));

        Map<Long, Notification> notifications = notificationRepository.findAllById(
                        deliveries.stream().map(PushDelivery::getNotificationId).toList()
                ).stream()
                .collect(Collectors.toMap(Notification::getId, notification -> notification));

        return deliveries.stream()
                .map(delivery -> toTask(delivery, notifications.get(delivery.getNotificationId())))
                .toList();
    }

    @Transactional
    public void markSucceeded(Long deliveryId) {
        update(deliveryId, delivery -> delivery.markSucceeded(LocalDateTime.now()));
    }

    @Transactional
    public void recordRetryableFailure(
            Long deliveryId,
            String errorMessage,
            Duration retryDelay,
            int maxAttemptCount
    ) {
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay는 0 이상이어야 합니다.");
        }
        update(deliveryId, delivery -> delivery.recordRetryableFailure(
                errorMessage,
                LocalDateTime.now().plus(retryDelay),
                maxAttemptCount
        ));
    }

    @Transactional
    public void markFailed(Long deliveryId, String errorMessage) {
        update(deliveryId, delivery -> delivery.markFailed(errorMessage));
    }

    @Transactional
    public void markPermissionDenied(Long deliveryId, String errorMessage) {
        update(deliveryId, delivery -> delivery.markPermissionDenied(errorMessage));
    }

    @Transactional
    public void markInvalidToken(Long deliveryId, String errorMessage) {
        update(deliveryId, delivery -> delivery.markInvalidToken(errorMessage));
    }

    private PushDeliveryTask toTask(PushDelivery delivery, Notification notification) {
        if (notification == null) {
            throw new IllegalStateException("Push Delivery의 알림을 찾을 수 없습니다.");
        }
        return new PushDeliveryTask(
                delivery.getId(),
                delivery.getFcmRegistrationId(),
                new PushMessage(
                        notification.getTitle(),
                        notification.getBody(),
                        notification.getLinkUrl()
                )
        );
    }

    private void update(Long deliveryId, Consumer<PushDelivery> updateAction) {
        if (deliveryId == null || deliveryId <= 0) {
            throw new IllegalArgumentException("deliveryId는 양수여야 합니다.");
        }
        PushDelivery delivery = pushDeliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Push Delivery를 찾을 수 없습니다."));
        updateAction.accept(delivery);
    }

    private static void validateBatchSize(int batchSize) {
        if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize는 1 이상 100 이하여야 합니다.");
        }
    }
}

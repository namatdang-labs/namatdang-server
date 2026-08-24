package com.namatdang.namatdang.notification.handler;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.entity.NotificationType;
import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.handler.entity.NotificationEventConsumption;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.push.PushTarget;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class DealCreatedNotificationHandler implements NotificationEventMessageHandler {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final FavoriteRecipientReader favoriteRecipientReader;
    private final ActivePushRegistrationReader pushRegistrationReader;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final NotificationEventConsumptionRepository consumptionRepository;

    public DealCreatedNotificationHandler(
            FavoriteRecipientReader favoriteRecipientReader,
            ActivePushRegistrationReader pushRegistrationReader,
            NotificationRepository notificationRepository,
            PushDeliveryRepository pushDeliveryRepository,
            NotificationEventConsumptionRepository consumptionRepository
    ) {
        this.favoriteRecipientReader = favoriteRecipientReader;
        this.pushRegistrationReader = pushRegistrationReader;
        this.notificationRepository = notificationRepository;
        this.pushDeliveryRepository = pushDeliveryRepository;
        this.consumptionRepository = consumptionRepository;
    }

    @Override
    @Transactional
    public void handle(NotificationEventMessage message) {
        validate(message);
        if (consumptionRepository.existsById(message.eventId())) {
            return;
        }
        consumptionRepository.saveAndFlush(new NotificationEventConsumption(message.eventId()));

        Set<Long> recipientIds = normalizedRecipientIds(
                favoriteRecipientReader.findUserIdsByStoreId(message.storeId())
        );
        if (recipientIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = recipientIds.stream()
                .map(recipientId -> new Notification(
                        message.eventId(),
                        recipientId,
                        NotificationType.DEAL_CREATED,
                        "새로운 마감 할인이 등록됐어요",
                        "즐겨찾기한 매장에 새로운 Deal이 등록됐어요.",
                        "/deals/%d".formatted(message.dealId())
                ))
                .toList();
        notifications = notificationRepository.saveAllAndFlush(notifications);

        Map<Long, Notification> notificationByRecipient = notifications.stream()
                .collect(Collectors.toMap(Notification::getRecipientUserId, Function.identity()));
        List<PushDelivery> deliveries = pushRegistrationReader.findAllByUserIds(recipientIds).stream()
                .filter(target -> notificationByRecipient.containsKey(target.userId()))
                .map(target -> toDelivery(target, notificationByRecipient))
                .toList();
        pushDeliveryRepository.saveAll(deliveries);
    }

    private PushDelivery toDelivery(
            PushTarget target,
            Map<Long, Notification> notificationByRecipient
    ) {
        return PushDelivery.pending(
                notificationByRecipient.get(target.userId()).getId(),
                target.registrationId()
        );
    }

    private Set<Long> normalizedRecipientIds(List<Long> recipientIds) {
        if (recipientIds == null) {
            throw new IllegalStateException("즐겨찾기 수신자 조회 결과는 null일 수 없습니다.");
        }
        return recipientIds.stream()
                .filter(recipientId -> recipientId != null && recipientId > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validate(NotificationEventMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
        if (message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 메시지 스키마 버전입니다.");
        }
        if (message.eventType() != NotificationEventType.DEAL_CREATED) {
            throw new IllegalArgumentException("DEAL_CREATED 이벤트만 처리할 수 있습니다.");
        }
        requirePositive(message.eventId(), "eventId");
        requirePositive(message.dealId(), "dealId");
        requirePositive(message.storeId(), "storeId");
        if (message.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt은 필수입니다.");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }
}

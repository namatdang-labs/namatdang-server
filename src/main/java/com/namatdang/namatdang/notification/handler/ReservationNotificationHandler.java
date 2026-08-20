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
import com.namatdang.namatdang.notification.recipient.ReservationNotificationRecipients;
import com.namatdang.namatdang.notification.recipient.ReservationRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class ReservationNotificationHandler implements NotificationEventMessageHandler {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ReservationRecipientReader reservationRecipientReader;
    private final ActivePushRegistrationReader pushRegistrationReader;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final NotificationEventConsumptionRepository consumptionRepository;

    public ReservationNotificationHandler(
            ReservationRecipientReader reservationRecipientReader,
            ActivePushRegistrationReader pushRegistrationReader,
            NotificationRepository notificationRepository,
            PushDeliveryRepository pushDeliveryRepository,
            NotificationEventConsumptionRepository consumptionRepository
    ) {
        this.reservationRecipientReader = reservationRecipientReader;
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

        ReservationNotificationRecipients recipients = reservationRecipientReader
                .findByReservationId(message.reservationId())
                .orElseThrow(() -> new IllegalStateException("예약 알림 수신자를 찾을 수 없습니다."));
        if (!recipients.storeId().equals(message.storeId())) {
            throw new IllegalStateException("예약 이벤트의 매장 정보가 일치하지 않습니다.");
        }

        List<Notification> notifications = notificationRepository.saveAllAndFlush(
                createNotifications(message, recipients)
        );
        Map<Long, Notification> notificationByRecipient = notifications.stream()
                .collect(Collectors.toMap(Notification::getRecipientUserId, Function.identity()));

        Set<Long> recipientIds = new LinkedHashSet<>(notificationByRecipient.keySet());
        List<PushDelivery> deliveries = pushRegistrationReader.findAllByUserIds(recipientIds).stream()
                .filter(target -> notificationByRecipient.containsKey(target.userId()))
                .map(target -> toDelivery(target, notificationByRecipient))
                .toList();
        pushDeliveryRepository.saveAll(deliveries);
    }

    private List<Notification> createNotifications(
            NotificationEventMessage message,
            ReservationNotificationRecipients recipients
    ) {
        NotificationType type = toNotificationType(message.eventType());
        String consumerTitle;
        String consumerBody;
        String ownerTitle;
        String ownerBody;

        if (message.eventType() == NotificationEventType.RESERVATION_CONFIRMED) {
            consumerTitle = "예약이 확정됐어요";
            consumerBody = "예약이 정상적으로 완료됐어요.";
            ownerTitle = "새로운 예약이 들어왔어요";
            ownerBody = "새로운 예약이 확정됐어요.";
        } else {
            consumerTitle = "예약이 취소됐어요";
            consumerBody = "예약 취소가 완료됐어요.";
            ownerTitle = "예약이 취소됐어요";
            ownerBody = "고객이 예약을 취소했어요.";
        }

        return List.of(
                new Notification(
                        message.eventId(),
                        recipients.consumerUserId(),
                        type,
                        consumerTitle,
                        consumerBody,
                        "/reservations/%d".formatted(message.reservationId())
                ),
                new Notification(
                        message.eventId(),
                        recipients.ownerUserId(),
                        type,
                        ownerTitle,
                        ownerBody,
                        "/owner/reservations/%d".formatted(message.reservationId())
                )
        );
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

    private NotificationType toNotificationType(NotificationEventType eventType) {
        return switch (eventType) {
            case RESERVATION_CONFIRMED -> NotificationType.RESERVATION_CONFIRMED;
            case RESERVATION_CANCELED -> NotificationType.RESERVATION_CANCELED;
            default -> throw new IllegalArgumentException("예약 이벤트만 처리할 수 있습니다.");
        };
    }

    private void validate(NotificationEventMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
        if (message.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 메시지 스키마 버전입니다.");
        }
        if (message.eventType() != NotificationEventType.RESERVATION_CONFIRMED
                && message.eventType() != NotificationEventType.RESERVATION_CANCELED) {
            throw new IllegalArgumentException("예약 이벤트만 처리할 수 있습니다.");
        }
        requirePositive(message.reservationId(), "reservationId");
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

package com.namatdang.namatdang.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.entity.NotificationType;
import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.fixture.FakePushRegistrationStore;
import com.namatdang.namatdang.notification.fixture.FakeReservationRecipientReader;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.push.PushTarget;
import com.namatdang.namatdang.notification.recipient.ReservationNotificationRecipients;
import com.namatdang.namatdang.notification.recipient.ReservationRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ReservationNotificationHandlerTests.TestConfig.class)
class ReservationNotificationHandlerTests extends IntegrationTestSupport {

    private static final Long EVENT_ID = 11_001L;
    private static final Long RESERVATION_ID = 12_001L;
    private static final Long STORE_ID = 13_001L;
    private static final Long CONSUMER_ID = 14_001L;
    private static final Long OWNER_ID = 14_002L;
    private static final Long OTHER_OWNER_ID = 14_003L;
    private static final Long CONSUMER_REGISTRATION_ID = 15_001L;
    private static final Long OWNER_REGISTRATION_ID = 15_002L;

    @Autowired
    private ReservationNotificationHandler handler;

    @Autowired
    private FakeReservationRecipientReader reservationRecipientReader;

    @Autowired
    private FakePushRegistrationStore pushRegistrationStore;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushDeliveryRepository pushDeliveryRepository;

    @Autowired
    private NotificationEventConsumptionRepository consumptionRepository;

    @BeforeEach
    void setUp() {
        clearDatabase();
        reservationRecipientReader.reset();
        pushRegistrationStore.reset();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    void createConfirmedNotificationsAndPushDeliveriesForConsumerAndOwner() {
        setRecipients(CONSUMER_ID, OWNER_ID);
        pushRegistrationStore.add(new PushTarget(CONSUMER_REGISTRATION_ID, CONSUMER_ID, "consumer-token"));
        pushRegistrationStore.add(new PushTarget(OWNER_REGISTRATION_ID, OWNER_ID, "owner-token"));

        handler.handle(message(EVENT_ID, NotificationEventType.RESERVATION_CONFIRMED));

        List<Notification> notifications = notificationRepository.findAllByEventId(EVENT_ID);
        assertThat(notifications)
                .extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(CONSUMER_ID, OWNER_ID);
        assertThat(notifications).allSatisfy(notification ->
                assertThat(notification.getNotificationType())
                        .isEqualTo(NotificationType.RESERVATION_CONFIRMED));
        assertThat(notificationFor(notifications, CONSUMER_ID).getLinkUrl())
                .isEqualTo("/reservations/" + RESERVATION_ID);
        assertThat(notificationFor(notifications, OWNER_ID).getLinkUrl())
                .isEqualTo("/owner/reservations/" + RESERVATION_ID);

        assertThat(pushDeliveryRepository.findAll())
                .extracting(PushDelivery::getFcmRegistrationId)
                .containsExactlyInAnyOrder(CONSUMER_REGISTRATION_ID, OWNER_REGISTRATION_ID);
        assertThat(consumptionRepository.existsById(EVENT_ID)).isTrue();
    }

    @Test
    void createCanceledNotificationsForConsumerAndOwner() {
        setRecipients(CONSUMER_ID, OWNER_ID);

        handler.handle(message(EVENT_ID, NotificationEventType.RESERVATION_CANCELED));

        List<Notification> notifications = notificationRepository.findAllByEventId(EVENT_ID);
        assertThat(notifications).hasSize(2);
        assertThat(notifications).allSatisfy(notification -> {
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RESERVATION_CANCELED);
            assertThat(notification.getTitle()).isEqualTo("예약이 취소됐어요");
        });
        assertThat(notificationFor(notifications, CONSUMER_ID).getBody())
                .isEqualTo("예약 취소가 완료됐어요.");
        assertThat(notificationFor(notifications, OWNER_ID).getBody())
                .isEqualTo("고객이 예약을 취소했어요.");
    }

    @Test
    void ignoreDuplicatedMessageWithoutCreatingNotificationsForChangedRecipients() {
        setRecipients(CONSUMER_ID, OWNER_ID);
        handler.handle(message(EVENT_ID, NotificationEventType.RESERVATION_CONFIRMED));

        setRecipients(CONSUMER_ID, OTHER_OWNER_ID);
        handler.handle(message(EVENT_ID, NotificationEventType.RESERVATION_CONFIRMED));

        assertThat(notificationRepository.findAllByEventId(EVENT_ID))
                .extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(CONSUMER_ID, OWNER_ID);
        assertThat(consumptionRepository.count()).isEqualTo(1);
    }

    @Test
    void rollbackConsumptionAndNotificationsWhenPushLookupFails() {
        setRecipients(CONSUMER_ID, OWNER_ID);
        pushRegistrationStore.failBulkRead();

        assertThatThrownBy(() -> handler.handle(
                message(EVENT_ID, NotificationEventType.RESERVATION_CONFIRMED)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Push 등록정보 조회 실패");

        assertThat(consumptionRepository.existsById(EVENT_ID)).isFalse();
        assertThat(notificationRepository.findAllByEventId(EVENT_ID)).isEmpty();
    }

    @Test
    void rejectMissingReservationRecipientsAndLeaveEventRetryable() {
        assertThatThrownBy(() -> handler.handle(
                message(EVENT_ID, NotificationEventType.RESERVATION_CONFIRMED)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("예약 알림 수신자를 찾을 수 없습니다.");

        assertThat(consumptionRepository.existsById(EVENT_ID)).isFalse();
        assertThat(notificationRepository.findAllByEventId(EVENT_ID)).isEmpty();
    }

    private void setRecipients(Long consumerId, Long ownerId) {
        reservationRecipientReader.setRecipients(
                RESERVATION_ID,
                new ReservationNotificationRecipients(consumerId, ownerId, STORE_ID)
        );
    }

    private NotificationEventMessage message(Long eventId, NotificationEventType eventType) {
        return NotificationEventMessage.create(
                eventId,
                eventType,
                null,
                RESERVATION_ID,
                STORE_ID,
                LocalDateTime.of(2026, 8, 20, 16, 0)
        );
    }

    private Notification notificationFor(List<Notification> notifications, Long recipientId) {
        return notifications.stream()
                .filter(notification -> notification.getRecipientUserId().equals(recipientId))
                .findFirst()
                .orElseThrow();
    }

    private void clearDatabase() {
        pushDeliveryRepository.deleteAll();
        notificationRepository.deleteAll();
        consumptionRepository.deleteAll();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        FakeReservationRecipientReader reservationRecipientReader() {
            return new FakeReservationRecipientReader();
        }

        @Bean
        FakePushRegistrationStore pushRegistrationStore() {
            return new FakePushRegistrationStore();
        }

        @Bean
        ReservationNotificationHandler reservationNotificationHandler(
                ReservationRecipientReader reservationRecipientReader,
                FakePushRegistrationStore pushRegistrationStore,
                NotificationRepository notificationRepository,
                PushDeliveryRepository pushDeliveryRepository,
                NotificationEventConsumptionRepository consumptionRepository
        ) {
            return new ReservationNotificationHandler(
                    reservationRecipientReader,
                    pushRegistrationStore,
                    notificationRepository,
                    pushDeliveryRepository,
                    consumptionRepository
            );
        }
    }
}

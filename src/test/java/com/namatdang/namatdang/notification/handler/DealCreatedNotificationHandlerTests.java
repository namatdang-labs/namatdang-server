package com.namatdang.namatdang.notification.handler;

import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.DEAL_ID;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.EVENT_ID;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.FAVORITE_USER_A;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.FAVORITE_USER_B;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_A1;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_A2;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_B1;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_UNRELATED;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.STORE_ID;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.UNRELATED_USER;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.dealCreatedMessage;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.target;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.entity.NotificationType;
import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.fixture.FakeFavoriteRecipientReader;
import com.namatdang.namatdang.notification.fixture.FakePushRegistrationStore;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import com.namatdang.namatdang.notification.service.NotificationCleanupService;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(DealCreatedNotificationHandlerTests.TestConfig.class)
class DealCreatedNotificationHandlerTests extends IntegrationTestSupport {

    @Autowired
    private DealCreatedNotificationHandler handler;

    @Autowired
    private FakeFavoriteRecipientReader favoriteRecipientReader;

    @Autowired
    private FakePushRegistrationStore pushRegistrationStore;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushDeliveryRepository pushDeliveryRepository;

    @Autowired
    private NotificationEventConsumptionRepository consumptionRepository;

    @Autowired
    private NotificationCleanupService notificationCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearDatabase();
        favoriteRecipientReader.reset();
        pushRegistrationStore.reset();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    private void clearDatabase() {
        pushDeliveryRepository.deleteAll();
        notificationRepository.deleteAll();
        consumptionRepository.deleteAll();
    }

    @Test
    void createNotificationsAndDeliveriesFromDummyContracts() {
        favoriteRecipientReader.setRecipients(
                STORE_ID,
                List.of(FAVORITE_USER_A, FAVORITE_USER_B, FAVORITE_USER_B)
        );
        pushRegistrationStore.add(target(REGISTRATION_A1, FAVORITE_USER_A, "token-A"));
        pushRegistrationStore.add(target(REGISTRATION_A2, FAVORITE_USER_A, "token-B"));
        pushRegistrationStore.add(target(REGISTRATION_B1, FAVORITE_USER_B, "token-C"));
        pushRegistrationStore.add(target(
                REGISTRATION_UNRELATED,
                UNRELATED_USER,
                "token-unrelated"
        ));

        handler.handle(dealCreatedMessage());

        List<Notification> notifications = notificationRepository.findAllByEventId(EVENT_ID);
        assertThat(notifications)
                .extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(FAVORITE_USER_A, FAVORITE_USER_B);
        assertThat(notifications).allSatisfy(notification -> {
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.DEAL_CREATED);
            assertThat(notification.getLinkUrl()).isEqualTo("/deals/" + DEAL_ID);
        });

        List<PushDelivery> deliveries = pushDeliveryRepository.findAll();
        assertThat(deliveries).hasSize(3);
        assertThat(deliveries)
                .extracting(PushDelivery::getFcmRegistrationId)
                .containsExactlyInAnyOrder(REGISTRATION_A1, REGISTRATION_A2, REGISTRATION_B1);
        assertThat(consumptionRepository.existsById(EVENT_ID)).isTrue();
    }

    @Test
    void ignoreDuplicatedMessageWithoutCreatingNewRecipientsOrTokens() {
        favoriteRecipientReader.setRecipients(STORE_ID, List.of(FAVORITE_USER_A));
        pushRegistrationStore.add(target(REGISTRATION_A1, FAVORITE_USER_A, "token-A"));
        handler.handle(dealCreatedMessage());

        favoriteRecipientReader.setRecipients(
                STORE_ID,
                List.of(FAVORITE_USER_A, FAVORITE_USER_B)
        );
        pushRegistrationStore.add(target(REGISTRATION_B1, FAVORITE_USER_B, "token-C"));
        handler.handle(dealCreatedMessage());

        assertThat(notificationRepository.findAllByEventId(EVENT_ID))
                .extracting(Notification::getRecipientUserId)
                .containsExactly(FAVORITE_USER_A);
        assertThat(pushDeliveryRepository.findAll()).hasSize(1);
    }

    @Test
    void rememberEventEvenWhenThereAreNoRecipients() {
        handler.handle(dealCreatedMessage());

        favoriteRecipientReader.setRecipients(STORE_ID, List.of(FAVORITE_USER_A));
        handler.handle(dealCreatedMessage());

        assertThat(consumptionRepository.existsById(EVENT_ID)).isTrue();
        assertThat(notificationRepository.findAllByEventId(EVENT_ID)).isEmpty();
    }

    @Test
    void rollbackConsumptionAndNotificationsWhenTokenLookupFails() {
        favoriteRecipientReader.setRecipients(STORE_ID, List.of(FAVORITE_USER_A));
        pushRegistrationStore.failBulkRead();

        assertThatThrownBy(() -> handler.handle(dealCreatedMessage()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Push 등록정보 조회 실패");

        assertThat(consumptionRepository.existsById(EVENT_ID)).isFalse();
        assertThat(notificationRepository.findAllByEventId(EVENT_ID)).isEmpty();
    }

    @Test
    void rejectUnsupportedMessageSchema() {
        NotificationEventMessage unsupportedMessage = new NotificationEventMessage(
                2,
                EVENT_ID,
                NotificationEventType.DEAL_CREATED,
                DEAL_ID,
                null,
                STORE_ID,
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> handler.handle(unsupportedMessage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 메시지 스키마 버전입니다.");
    }

    @Test
    void cleanExpiredNotificationsDeliveriesAndConsumptionMarkersTogether() {
        favoriteRecipientReader.setRecipients(STORE_ID, List.of(FAVORITE_USER_A));
        pushRegistrationStore.add(target(REGISTRATION_A1, FAVORITE_USER_A, "token-A"));
        handler.handle(dealCreatedMessage());
        LocalDateTime expiredAt = LocalDateTime.now().minusDays(31);
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE event_id = ?",
                Timestamp.valueOf(expiredAt),
                EVENT_ID
        );
        jdbcTemplate.update(
                "UPDATE notification_event_consumptions SET processed_at = ? WHERE event_id = ?",
                Timestamp.valueOf(expiredAt),
                EVENT_ID
        );

        notificationCleanupService.deleteExpiredNotifications();

        assertThat(notificationRepository.findAllByEventId(EVENT_ID)).isEmpty();
        assertThat(pushDeliveryRepository.findAll()).isEmpty();
        assertThat(consumptionRepository.existsById(EVENT_ID)).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        FakeFavoriteRecipientReader favoriteRecipientReader() {
            return new FakeFavoriteRecipientReader();
        }

        @Bean
        FakePushRegistrationStore pushRegistrationStore() {
            return new FakePushRegistrationStore();
        }

        @Bean
        DealCreatedNotificationHandler dealCreatedNotificationHandler(
                FavoriteRecipientReader favoriteRecipientReader,
                FakePushRegistrationStore pushRegistrationStore,
                NotificationRepository notificationRepository,
                PushDeliveryRepository pushDeliveryRepository,
                NotificationEventConsumptionRepository consumptionRepository
        ) {
            return new DealCreatedNotificationHandler(
                    favoriteRecipientReader,
                    pushRegistrationStore,
                    notificationRepository,
                    pushDeliveryRepository,
                    consumptionRepository
            );
        }
    }
}

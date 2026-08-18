package com.namatdang.namatdang.notification.delivery;

import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.FAVORITE_USER_A;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.FAVORITE_USER_B;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_A1;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_A2;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.REGISTRATION_B1;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.STORE_ID;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.dealCreatedMessage;
import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.target;
import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.entity.PushDeliveryStatus;
import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.delivery.service.PushDeliveryProcessor;
import com.namatdang.namatdang.notification.delivery.service.PushDeliveryService;
import com.namatdang.namatdang.notification.fixture.FakeFavoriteRecipientReader;
import com.namatdang.namatdang.notification.fixture.FakePushMessageSender;
import com.namatdang.namatdang.notification.fixture.FakePushRegistrationStore;
import com.namatdang.namatdang.notification.handler.DealCreatedNotificationHandler;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.push.ActivePushRegistrationReader;
import com.namatdang.namatdang.notification.push.InvalidPushRegistrationHandler;
import com.namatdang.namatdang.notification.push.PushMessageSender;
import com.namatdang.namatdang.notification.push.PushSendResult;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PushDeliveryProcessorTests.TestConfig.class)
class PushDeliveryProcessorTests {

    private static final Duration SENDING_TIMEOUT = Duration.ofMinutes(5);

    @Autowired
    private DealCreatedNotificationHandler handler;

    @Autowired
    private PushDeliveryProcessor processor;

    @Autowired
    private FakeFavoriteRecipientReader favoriteRecipientReader;

    @Autowired
    private FakePushRegistrationStore pushRegistrationStore;

    @Autowired
    private FakePushMessageSender pushMessageSender;

    @Autowired
    private PushDeliveryRepository pushDeliveryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventConsumptionRepository consumptionRepository;

    @BeforeEach
    void setUp() {
        clearDatabase();
        favoriteRecipientReader.reset();
        pushRegistrationStore.reset();
        pushMessageSender.reset();

        favoriteRecipientReader.setRecipients(
                STORE_ID,
                List.of(FAVORITE_USER_A, FAVORITE_USER_B)
        );
        pushRegistrationStore.add(target(REGISTRATION_A1, FAVORITE_USER_A, "token-A"));
        pushRegistrationStore.add(target(REGISTRATION_A2, FAVORITE_USER_A, "token-B"));
        pushRegistrationStore.add(target(REGISTRATION_B1, FAVORITE_USER_B, "token-C"));
        handler.handle(dealCreatedMessage());
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
    void handleSuccessInvalidTokenAndRetryIndependently() {
        pushMessageSender.willReturn("token-A", PushSendResult.succeeded());
        pushMessageSender.willReturn(
                "token-B",
                PushSendResult.invalidToken("UNREGISTERED")
        );
        pushMessageSender.willReturn(
                "token-C",
                PushSendResult.retryableFailure("FCM unavailable"),
                PushSendResult.succeeded()
        );

        assertThat(processor.processBatch(10, SENDING_TIMEOUT, Duration.ZERO, 3)).isEqualTo(3);

        Map<Long, PushDelivery> firstAttempt = deliveriesByRegistrationId();
        assertThat(firstAttempt.get(REGISTRATION_A1).getStatus())
                .isEqualTo(PushDeliveryStatus.SUCCEEDED);
        assertThat(firstAttempt.get(REGISTRATION_A2).getStatus())
                .isEqualTo(PushDeliveryStatus.INVALID_TOKEN);
        assertThat(firstAttempt.get(REGISTRATION_B1).getStatus())
                .isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(pushRegistrationStore.findById(REGISTRATION_A2)).isEmpty();

        assertThat(processor.processBatch(10, SENDING_TIMEOUT, Duration.ZERO, 3)).isEqualTo(1);
        assertThat(deliveriesByRegistrationId().get(REGISTRATION_B1).getStatus())
                .isEqualTo(PushDeliveryStatus.SUCCEEDED);
        assertThat(pushMessageSender.attemptedTokens())
                .containsExactlyInAnyOrder("token-A", "token-B", "token-C", "token-C");
    }

    @Test
    void stopRetryingAfterMaximumAttempts() {
        pushMessageSender.willReturn(
                "token-A",
                PushSendResult.retryableFailure("first failure"),
                PushSendResult.retryableFailure("second failure")
        );
        pushMessageSender.willReturn("token-B", PushSendResult.succeeded());
        pushMessageSender.willReturn("token-C", PushSendResult.succeeded());

        processor.processBatch(10, SENDING_TIMEOUT, Duration.ZERO, 2);
        processor.processBatch(10, SENDING_TIMEOUT, Duration.ZERO, 2);

        PushDelivery failed = deliveriesByRegistrationId().get(REGISTRATION_A1);
        assertThat(failed.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(2);
        assertThat(failed.getLastError()).isEqualTo("second failure");
    }

    @Test
    void retryWhenInvalidTokenDeletionTemporarilyFails() {
        pushMessageSender.willReturn("token-A", PushSendResult.succeeded());
        pushMessageSender.willReturn(
                "token-B",
                PushSendResult.invalidToken("UNREGISTERED")
        );
        pushMessageSender.willReturn("token-C", PushSendResult.succeeded());
        pushRegistrationStore.failInvalidation();

        processor.processBatch(10, SENDING_TIMEOUT, Duration.ZERO, 3);

        PushDelivery retryable = deliveriesByRegistrationId().get(REGISTRATION_A2);
        assertThat(retryable.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(retryable.getRetryCount()).isEqualTo(1);
        assertThat(retryable.getLastError()).isEqualTo("Push 등록정보 삭제 실패");
        assertThat(pushRegistrationStore.findById(REGISTRATION_A2)).isPresent();
    }

    private Map<Long, PushDelivery> deliveriesByRegistrationId() {
        return pushDeliveryRepository.findAll().stream()
                .collect(Collectors.toMap(PushDelivery::getFcmRegistrationId, delivery -> delivery));
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
        FakePushMessageSender pushMessageSender() {
            return new FakePushMessageSender();
        }

        @Bean
        DealCreatedNotificationHandler dealCreatedNotificationHandler(
                FavoriteRecipientReader favoriteRecipientReader,
                ActivePushRegistrationReader pushRegistrationReader,
                NotificationRepository notificationRepository,
                PushDeliveryRepository pushDeliveryRepository,
                NotificationEventConsumptionRepository consumptionRepository
        ) {
            return new DealCreatedNotificationHandler(
                    favoriteRecipientReader,
                    pushRegistrationReader,
                    notificationRepository,
                    pushDeliveryRepository,
                    consumptionRepository
            );
        }

        @Bean
        PushDeliveryProcessor pushDeliveryProcessor(
                PushDeliveryService pushDeliveryService,
                ActivePushRegistrationReader pushRegistrationReader,
                PushMessageSender pushMessageSender,
                InvalidPushRegistrationHandler invalidPushRegistrationHandler
        ) {
            return new PushDeliveryProcessor(
                    pushDeliveryService,
                    pushRegistrationReader,
                    pushMessageSender,
                    invalidPushRegistrationHandler
            );
        }
    }
}

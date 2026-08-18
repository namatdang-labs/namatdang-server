package com.namatdang.namatdang.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.repository.PushDeliveryRepository;
import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.fixture.FakeFavoriteRecipientReader;
import com.namatdang.namatdang.notification.handler.DealCreatedNotificationHandler;
import com.namatdang.namatdang.notification.handler.repository.NotificationEventConsumptionRepository;
import com.namatdang.namatdang.notification.push.PushTarget;
import com.namatdang.namatdang.notification.recipient.FavoriteRecipientReader;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import com.namatdang.namatdang.push.adapter.FcmRegistrationNotificationAdapter;
import com.namatdang.namatdang.push.entity.FcmRegistration;
import com.namatdang.namatdang.push.entity.PushDeviceType;
import com.namatdang.namatdang.push.repository.FcmRegistrationRepository;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(FcmRegistrationNotificationAdapterTests.TestConfig.class)
@Transactional
class FcmRegistrationNotificationAdapterTests {

    private static final long EVENT_ID = 91_001L;
    private static final long DEAL_ID = 92_001L;
    private static final long STORE_ID = 93_001L;

    @Autowired
    private FcmRegistrationNotificationAdapter adapter;

    @Autowired
    private FakeFavoriteRecipientReader favoriteRecipientReader;

    @Autowired
    private DealCreatedNotificationHandler handler;

    @Autowired
    private FcmRegistrationRepository fcmRegistrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushDeliveryRepository pushDeliveryRepository;

    @Test
    void readOnlyRegistrationsOwnedByRequestedUsers() {
        User recipient = saveUser("recipient");
        User anotherRecipient = saveUser("another");
        User unrelated = saveUser("unrelated");
        FcmRegistration first = saveRegistration(recipient, "token-A");
        FcmRegistration second = saveRegistration(anotherRecipient, "token-B");
        saveRegistration(unrelated, "token-unrelated");

        List<PushTarget> targets = adapter.findAllByUserIds(
                List.of(recipient.getId(), anotherRecipient.getId())
        );

        assertThat(targets)
                .extracting(PushTarget::registrationId)
                .containsExactly(first.getId(), second.getId());
        assertThat(targets)
                .extracting(PushTarget::userId)
                .containsExactly(recipient.getId(), anotherRecipient.getId());
        assertThat(adapter.findById(first.getId()))
                .get()
                .extracting(PushTarget::registrationToken)
                .isEqualTo("token-A");
        assertThat(adapter.findAllByUserIds(List.of())).isEmpty();
    }

    @Test
    void deleteInvalidRegistrationIdempotently() {
        FcmRegistration registration = saveRegistration(saveUser("invalid"), "invalid-token");

        adapter.invalidate(registration.getId());
        adapter.invalidate(registration.getId());

        assertThat(fcmRegistrationRepository.findById(registration.getId())).isEmpty();
    }

    @Test
    void createPushDeliveriesUsingActualFcmRegistrations() {
        User firstRecipient = saveUser("favorite-a");
        User secondRecipient = saveUser("favorite-b");
        User unrelated = saveUser("not-favorite");
        FcmRegistration firstDevice = saveRegistration(firstRecipient, "token-A1");
        FcmRegistration secondDevice = saveRegistration(firstRecipient, "token-A2");
        FcmRegistration thirdDevice = saveRegistration(secondRecipient, "token-B1");
        saveRegistration(unrelated, "token-unrelated");
        favoriteRecipientReader.setRecipients(
                STORE_ID,
                List.of(firstRecipient.getId(), secondRecipient.getId())
        );

        handler.handle(new NotificationEventMessage(
                1,
                EVENT_ID,
                NotificationEventType.DEAL_CREATED,
                DEAL_ID,
                STORE_ID,
                LocalDateTime.now()
        ));

        assertThat(notificationRepository.findAllByEventId(EVENT_ID))
                .extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(firstRecipient.getId(), secondRecipient.getId());
        assertThat(pushDeliveryRepository.findAll())
                .extracting(PushDelivery::getFcmRegistrationId)
                .containsExactlyInAnyOrder(
                        firstDevice.getId(),
                        secondDevice.getId(),
                        thirdDevice.getId()
                );
    }

    private User saveUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID() + "@example.com",
                "encoded-password",
                "테스트 사용자",
                "010-1234-5678",
                UserRole.CONSUMER
        ));
    }

    private FcmRegistration saveRegistration(User user, String token) {
        return fcmRegistrationRepository.saveAndFlush(new FcmRegistration(
                user,
                token,
                PushDeviceType.DESKTOP,
                "CHROME"
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        FakeFavoriteRecipientReader favoriteRecipientReader() {
            return new FakeFavoriteRecipientReader();
        }

        @Bean
        DealCreatedNotificationHandler dealCreatedNotificationHandler(
                FavoriteRecipientReader favoriteRecipientReader,
                FcmRegistrationNotificationAdapter fcmRegistrationAdapter,
                NotificationRepository notificationRepository,
                PushDeliveryRepository pushDeliveryRepository,
                NotificationEventConsumptionRepository consumptionRepository
        ) {
            return new DealCreatedNotificationHandler(
                    favoriteRecipientReader,
                    fcmRegistrationAdapter,
                    notificationRepository,
                    pushDeliveryRepository,
                    consumptionRepository
            );
        }
    }
}

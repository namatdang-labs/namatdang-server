package com.namatdang.namatdang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.entity.NotificationType;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import com.namatdang.namatdang.notification.service.NotificationCleanupService;
import com.namatdang.namatdang.security.JwtTokenProvider;
import com.namatdang.namatdang.support.IntegrationTestSupport;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import com.namatdang.namatdang.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationCleanupService notificationCleanupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getRecentNotificationsInDescendingOrder() throws Exception {
        User user = saveUser("list");
        User otherUser = saveUser("other-list");
        Notification first = saveNotification(101L, user.getId(), NotificationType.DEAL_CREATED);
        Notification second = saveNotification(102L, user.getId(), NotificationType.RESERVATION_CONFIRMED);
        Notification old = saveNotification(103L, user.getId(), NotificationType.RESERVATION_CANCELED);
        saveNotification(104L, otherUser.getId(), NotificationType.DEAL_CREATED);
        makeOlderThanThirtyDays(old);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].id").value(second.getId()))
                .andExpect(jsonPath("$.notifications[1].id").value(first.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getNotificationsWithCursor() throws Exception {
        User user = saveUser("cursor");
        Notification first = saveNotification(201L, user.getId(), NotificationType.DEAL_CREATED);
        Notification second = saveNotification(202L, user.getId(), NotificationType.RESERVATION_CONFIRMED);
        Notification third = saveNotification(203L, user.getId(), NotificationType.RESERVATION_CANCELED);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearerToken(user))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].id").value(third.getId()))
                .andExpect(jsonPath("$.notifications[1].id").value(second.getId()))
                .andExpect(jsonPath("$.nextCursor").value(second.getId()))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearerToken(user))
                        .param("cursor", second.getId().toString())
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(first.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getUnreadNotificationCount() throws Exception {
        User user = saveUser("unread");
        saveNotification(301L, user.getId(), NotificationType.DEAL_CREATED);
        saveNotification(302L, user.getId(), NotificationType.RESERVATION_CONFIRMED);
        Notification readNotification = saveNotification(
                303L,
                user.getId(),
                NotificationType.RESERVATION_CANCELED
        );
        Notification oldUnreadNotification = saveNotification(
                304L,
                user.getId(),
                NotificationType.DEAL_CREATED
        );
        readNotification.markAsRead();
        notificationRepository.flush();
        makeOlderThanThirtyDays(oldUnreadNotification);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void readMyNotification() throws Exception {
        User user = saveUser("read");
        Notification notification = saveNotification(401L, user.getId(), NotificationType.DEAL_CREATED);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", bearerToken(user)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Notification updatedNotification = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updatedNotification.isRead()).isTrue();
        assertThat(updatedNotification.getReadAt()).isNotNull();
    }

    @Test
    void readingNotificationIsIdempotent() throws Exception {
        User user = saveUser("idempotent-read");
        Notification notification = saveNotification(501L, user.getId(), NotificationType.DEAL_CREATED);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", bearerToken(user)))
                .andExpect(status().isNoContent());
        LocalDateTime firstReadAt = notification.getReadAt();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", bearerToken(user)))
                .andExpect(status().isNoContent());

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void cannotReadOtherUsersNotification() throws Exception {
        User owner = saveUser("owner");
        User otherUser = saveUser("other");
        Notification notification = saveNotification(601L, owner.getId(), NotificationType.DEAL_CREATED);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", bearerToken(otherUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void unknownNotificationReturnsNotFound() throws Exception {
        User user = saveUser("unknown");

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void invalidPaginationIsRejected() throws Exception {
        User user = saveUser("invalid-pagination");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearerToken(user))
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void duplicatedEventAndRecipientCannotBeSaved() {
        User user = saveUser("duplicate");
        saveNotification(701L, user.getId(), NotificationType.DEAL_CREATED);

        assertThatThrownBy(() -> saveNotification(
                701L,
                user.getId(),
                NotificationType.DEAL_CREATED
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteNotificationsOlderThanThirtyDays() {
        User user = saveUser("cleanup");
        Notification expiredNotification = saveNotification(
                801L,
                user.getId(),
                NotificationType.DEAL_CREATED
        );
        Notification recentNotification = saveNotification(
                802L,
                user.getId(),
                NotificationType.RESERVATION_CONFIRMED
        );
        makeOlderThanThirtyDays(expiredNotification);

        notificationCleanupService.deleteExpiredNotifications();

        assertThat(notificationRepository.findById(expiredNotification.getId())).isEmpty();
        assertThat(notificationRepository.findById(recentNotification.getId())).isPresent();
    }

    private User saveUser(String prefix) {
        User user = new User(
                prefix + "-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("password123"),
                "테스트회원",
                "010-1234-5678",
                UserRole.CONSUMER
        );
        return userRepository.saveAndFlush(user);
    }

    private Notification saveNotification(Long eventId, Long recipientUserId, NotificationType type) {
        Notification notification = new Notification(
                eventId,
                recipientUserId,
                type,
                "알림 제목",
                "알림 내용",
                "/notifications/target"
        );
        return notificationRepository.saveAndFlush(notification);
    }

    private void makeOlderThanThirtyDays(Notification notification) {
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(31)),
                notification.getId()
        );
        entityManager.clear();
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.issue(user.getId());
    }
}

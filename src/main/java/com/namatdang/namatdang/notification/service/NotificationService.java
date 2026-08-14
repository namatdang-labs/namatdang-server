package com.namatdang.namatdang.notification.service;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.notification.dto.NotificationListResponseDto;
import com.namatdang.namatdang.notification.dto.UnreadNotificationCountResponseDto;
import com.namatdang.namatdang.notification.entity.Notification;
import com.namatdang.namatdang.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationListResponseDto getNotifications(Long userId, Long cursor, int size) {
        validatePagination(cursor, size);

        LocalDateTime since = LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS);
        Pageable pageable = PageRequest.of(0, size);
        Slice<Notification> notificationSlice;

        if (cursor == null) {
            notificationSlice = notificationRepository
                    .findByRecipientUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(userId, since, pageable);
        } else {
            notificationSlice = notificationRepository
                    .findByRecipientUserIdAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
                            userId,
                            since,
                            cursor,
                            pageable
                    );
        }

        return NotificationListResponseDto.from(notificationSlice);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponseDto getUnreadCount(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS);
        long unreadCount = notificationRepository
                .countByRecipientUserIdAndReadFalseAndCreatedAtGreaterThanEqual(userId, since);
        return new UnreadNotificationCountResponseDto(unreadCount);
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessLogicException(ExceptionCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
    }

    private void validatePagination(Long cursor, int size) {
        if ((cursor != null && cursor <= 0) || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }
    }
}

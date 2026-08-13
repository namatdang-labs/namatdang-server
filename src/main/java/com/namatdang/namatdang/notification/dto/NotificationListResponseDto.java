package com.namatdang.namatdang.notification.dto;

import com.namatdang.namatdang.notification.entity.Notification;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Slice;

@Getter
@AllArgsConstructor
public class NotificationListResponseDto {

    private List<NotificationResponseDto> notifications;
    private Long nextCursor;
    private boolean hasNext;

    public static NotificationListResponseDto from(Slice<Notification> notificationSlice) {
        List<NotificationResponseDto> notifications = notificationSlice.getContent().stream()
                .map(NotificationResponseDto::from)
                .toList();
        Long nextCursor = notificationSlice.hasNext() && !notifications.isEmpty()
                ? notifications.getLast().getId()
                : null;

        return new NotificationListResponseDto(
                notifications,
                nextCursor,
                notificationSlice.hasNext()
        );
    }
}

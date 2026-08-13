package com.namatdang.namatdang.notification.controller;

import com.namatdang.namatdang.notification.dto.NotificationListResponseDto;
import com.namatdang.namatdang.notification.dto.UnreadNotificationCountResponseDto;
import com.namatdang.namatdang.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationListResponseDto> getNotifications(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        NotificationListResponseDto response = notificationService.getNotifications(userId, cursor, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadNotificationCountResponseDto> getUnreadCount(
            @RequestAttribute("userId") Long userId
    ) {
        UnreadNotificationCountResponseDto response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> readNotification(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.readNotification(userId, notificationId);
        return ResponseEntity.noContent().build();
    }
}

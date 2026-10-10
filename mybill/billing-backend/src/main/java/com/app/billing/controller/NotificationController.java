package com.app.billing.controller;

import com.app.billing.model.Notification;
import com.app.billing.service.NotificationService;
import com.app.billing.service.PermissionEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification System", description = "Centralized application-wide notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time Notification Stream", description = "Server-Sent Events (SSE) stream for real-time notifications")
    public SseEmitter streamNotifications() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        return notificationService.subscribeSse(current.getId());
    }

    @GetMapping
    @Operation(summary = "Get Notifications", description = "Get notification history for authenticated employee")
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        return ResponseEntity.ok(notificationService.getNotificationsForEmployee(current.getId(), read, module, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get Unread Count", description = "Get count of unread notifications for header bell")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            return ResponseEntity.ok(new UnreadCountResponse(0));
        }
        long count = notificationService.getUnreadCount(current.getId());
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark Notification as Read", description = "Mark a single notification as read")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        boolean success = notificationService.markAsRead(id, current.getId());
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark All as Read", description = "Mark all unread notifications as read for current employee")
    public ResponseEntity<Void> markAllAsRead() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        notificationService.markAllAsRead(current.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Notification", description = "Delete a notification from history")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        boolean deleted = notificationService.deleteNotification(id, current.getId());
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @Data
    public static class UnreadCountResponse {
        private final long unreadCount;
    }
}

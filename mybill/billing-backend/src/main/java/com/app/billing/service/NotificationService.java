package com.app.billing.service;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.NotificationRepository;
import com.app.billing.model.Employee;
import com.app.billing.model.Notification;
import com.app.billing.model.Notification.NotificationPriority;
import com.app.billing.model.Notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final PermissionEvaluatorService permissionEvaluator;

    private final Map<String, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribeSse(String employeeId) {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes timeout
        sseEmitters.computeIfAbsent(employeeId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(employeeId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for employee {}", employeeId);
            removeEmitter(employeeId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        });
        emitter.onError(e -> {
            log.debug("SSE emitter error for employee {}: {}", employeeId, e.getMessage());
            removeEmitter(employeeId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        });

        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to notification stream"));
        } catch (Exception e) {
            log.debug("Failed to send SSE INIT event for employee {}: {}", employeeId, e.getMessage());
            removeEmitter(employeeId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
        return emitter;
    }

    private void removeEmitter(String employeeId, SseEmitter emitter) {
        List<SseEmitter> list = sseEmitters.get(employeeId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                sseEmitters.remove(employeeId);
            }
        }
    }

    private void pushToSse(String recipientId, Notification notification) {
        List<SseEmitter> list = sseEmitters.get(recipientId);
        if (list != null && !list.isEmpty()) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("NOTIFICATION").data(notification));
                } catch (Exception e) {
                    log.debug("Failed to push notification via SSE to employee {}: {}", recipientId, e.getMessage());
                    removeEmitter(recipientId, emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        sseEmitters.forEach((empId, list) -> {
            for (SseEmitter emitter : new ArrayList<>(list)) {
                try {
                    emitter.send(SseEmitter.event().name("PING").data("keepalive"));
                } catch (Exception e) {
                    log.debug("Failed to send SSE heartbeat to employee {}, removing emitter: {}", empId, e.getMessage());
                    removeEmitter(empId, emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    /**
     * Send notification to a specific recipient employee.
     */
    public Notification sendNotification(
            String recipientEmployeeId,
            NotificationType type,
            String title,
            String message,
            String module,
            String entityType,
            String entityId,
            String navigationTarget,
            NotificationPriority priority,
            Map<String, String> metadata) {
        try {
            if (recipientEmployeeId == null || recipientEmployeeId.isBlank()) {
                log.warn("Cannot send notification: recipientEmployeeId is empty");
                return null;
            }

            Optional<Employee> empOpt = employeeRepository.findById(recipientEmployeeId);
            String recipientEmail = empOpt.map(Employee::getEmail).orElse(null);

            Notification notification = Notification.builder()
                    .recipientEmployeeId(recipientEmployeeId)
                    .recipientEmail(recipientEmail)
                    .type(type)
                    .title(title)
                    .message(message)
                    .module(module)
                    .entityType(entityType)
                    .entityId(entityId)
                    .navigationTarget(navigationTarget)
                    .priority(priority != null ? priority : NotificationPriority.NORMAL)
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .metadata(metadata)
                    .build();

            Notification saved = notificationRepository.save(notification);
            log.info("Notification [{}] created for employee ID: {}", type, recipientEmployeeId);

            pushToSse(recipientEmployeeId, saved);

            return saved;
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Dispatch notification to all eligible approvers/admins who have access/permission to approve or view the module.
     */
    public void sendNotificationToApprovers(
            String moduleKey,
            String action,
            NotificationType type,
            String title,
            String message,
            String entityType,
            String entityId,
            String navigationTarget,
            NotificationPriority priority,
            Map<String, String> metadata) {
        try {
            List<Employee> allEmployees = employeeRepository.findAll();
            List<String> recipientIds = new ArrayList<>();

            for (Employee emp : allEmployees) {
                if (emp.getStatus() == Employee.Status.INACTIVE) {
                    continue;
                }
                // Send if ADMIN role or has explicit action entitlement on module
                if (permissionEvaluator.isAdmin(emp) || permissionEvaluator.hasPermission(emp, moduleKey, action)) {
                    recipientIds.add(emp.getId());
                }
            }

            for (String recipientId : recipientIds) {
                sendNotification(recipientId, type, title, message, moduleKey, entityType, entityId, navigationTarget, priority, metadata);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch approver notifications: {}", e.getMessage(), e);
        }
    }

    public long getUnreadCount(String employeeId) {
        if (employeeId == null) return 0;
        return notificationRepository.countByRecipientEmployeeIdAndReadFalse(employeeId);
    }

    public List<Notification> getNotificationsForEmployee(String employeeId, Boolean readFilter, String moduleFilter, int page, int size) {
        if (employeeId == null) return List.of();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));

        if (readFilter != null && moduleFilter != null && !moduleFilter.isBlank()) {
            return notificationRepository.findByRecipientEmployeeIdAndReadAndModuleOrderByCreatedAtDesc(employeeId, readFilter, moduleFilter, pageable);
        } else if (readFilter != null) {
            return notificationRepository.findByRecipientEmployeeIdAndReadOrderByCreatedAtDesc(employeeId, readFilter, pageable);
        } else if (moduleFilter != null && !moduleFilter.isBlank()) {
            return notificationRepository.findByRecipientEmployeeIdAndModuleOrderByCreatedAtDesc(employeeId, moduleFilter, pageable);
        } else {
            return notificationRepository.findByRecipientEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        }
    }

    public boolean markAsRead(String notificationId, String employeeId) {
        try {
            Optional<Notification> opt = notificationRepository.findById(notificationId);
            if (opt.isPresent()) {
                Notification n = opt.get();
                // Security check: recipient match
                if (employeeId.equals(n.getRecipientEmployeeId())) {
                    n.setRead(true);
                    n.setReadAt(LocalDateTime.now());
                    notificationRepository.save(n);
                    return true;
                } else {
                    log.warn("Unauthorized markAsRead attempt by employee [{}] for notification [{}]", employeeId, notificationId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to mark notification as read: {}", e.getMessage(), e);
        }
        return false;
    }

    public void markAllAsRead(String employeeId) {
        try {
            List<Notification> unreadList = notificationRepository.findByRecipientEmployeeIdAndReadOrderByCreatedAtDesc(employeeId, false, PageRequest.of(0, 1000));
            LocalDateTime now = LocalDateTime.now();
            for (Notification n : unreadList) {
                n.setRead(true);
                n.setReadAt(now);
            }
            if (!unreadList.isEmpty()) {
                notificationRepository.saveAll(unreadList);
            }
        } catch (Exception e) {
            log.error("Failed to mark all notifications as read: {}", e.getMessage(), e);
        }
    }

    public boolean deleteNotification(String notificationId, String employeeId) {
        try {
            Optional<Notification> opt = notificationRepository.findById(notificationId);
            if (opt.isPresent()) {
                Notification n = opt.get();
                if (employeeId.equals(n.getRecipientEmployeeId())) {
                    notificationRepository.deleteById(notificationId);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete notification: {}", e.getMessage(), e);
        }
        return false;
    }
}

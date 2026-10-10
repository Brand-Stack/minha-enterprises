package com.app.billing.service;

import com.app.billing.dao.AuditLogRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.model.AuditLog;
import com.app.billing.model.Employee;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Records security-sensitive actions to the append-only {@code audit_logs} collection.
 * Logging failures are swallowed so auditing never breaks the primary business flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;

    /** Log an action for an explicitly-resolved employee (used at login before SecurityContext is set). */
    public void log(String action, String module, String targetId, String details,
                    String employeeId, String username, String role, String category) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .module(module)
                    .targetId(targetId)
                    .details(details)
                    .employeeId(employeeId)
                    .username(username)
                    .role(role)
                    .category(category)
                    .ipAddress(resolveIpAddress())
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {} module {}: {}", action, module, e.getMessage());
        }
    }

    /** Log an action for the currently-authenticated user (resolved from the SecurityContext). */
    public void log(String action, String module, String targetId, String details) {
        try {
            Employee current = resolveCurrentEmployee();
            String employeeId = current != null ? current.getId() : null;
            String username = current != null ? current.getEmail() : currentUsername();
            String role = current != null && current.getRole() != null ? current.getRole().name() : null;
            String category = current != null ? current.getCategory() : null;
            log(action, module, targetId, details, employeeId, username, role, category);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }

    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    private Employee resolveCurrentEmployee() {
        String username = currentUsername();
        if (username == null) {
            return null;
        }
        Optional<Employee> employee = employeeRepository.findFirstByEmailOrderByIdAsc(username);
        return employee.orElse(null);
    }

    private String currentUsername() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private String resolveIpAddress() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}

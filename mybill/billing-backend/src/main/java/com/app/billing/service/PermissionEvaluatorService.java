package com.app.billing.service;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.model.Employee;
import com.app.billing.model.ModulePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Core entitlement engine. Resolves whether the current (or a given) user may perform
 * an action on a module.
 *
 * <p>Policy:
 * <ul>
 *   <li>ADMIN role bypasses all checks (full, unrestricted access).</li>
 *   <li>Every other user is granted access only when their category's {@link ModulePermission}
 *       explicitly allows the requested action (default-deny).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionEvaluatorService {

    private final EmployeeRepository employeeRepository;
    private final EntitlementService entitlementService;

    /** Check permission for the currently authenticated user. */
    public boolean hasPermission(String moduleKey, String action) {
        if (isCurrentUserAdmin()) {
            return true;
        }
        Employee employee = currentEmployee();
        if (employee == null) {
            return false;
        }
        return hasPermission(employee, moduleKey, action);
    }

    public boolean hasPermission(Employee employee, String moduleKey, String action) {
        if (employee == null) {
            return false;
        }
        if (isAdmin(employee)) {
            return true;
        }
        ModulePermission permission = entitlementService.getModulePermission(employee.getCategoryId(), moduleKey);
        if (permission == null) {
            return false;
        }
        return permission.isAllowed(action);
    }

    /** Field-level visibility check for the current user. */
    public boolean hasFieldVisible(String moduleKey, String fieldKey) {
        if (isCurrentUserAdmin()) {
            return true;
        }
        Employee employee = currentEmployee();
        if (employee == null) {
            return false;
        }
        ModulePermission permission = entitlementService.getModulePermission(employee.getCategoryId(), moduleKey);
        if (permission == null || permission.getFields() == null) {
            return false;
        }
        var field = permission.getFields().get(fieldKey);
        return field != null && field.isVisible();
    }

    public boolean isAdmin(Employee employee) {
        return employee != null && employee.getRole() == Employee.Role.ADMIN;
    }

    /** True when the currently authenticated user is an ADMIN (unrestricted access). */
    public boolean isCurrentUserAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                if (auth.getAuthorities() != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()))) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check admin authority from context: {}", e.getMessage());
        }
        return isAdmin(currentEmployee());
    }

    /** Resolve the current user's {@link ModulePermission} for a module, or null when none applies. */
    public ModulePermission currentModulePermission(String moduleKey) {
        Employee employee = currentEmployee();
        if (employee == null) {
            return null;
        }
        return entitlementService.getModulePermission(employee.getCategoryId(), moduleKey);
    }

    public Employee currentEmployee() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            String identifier = auth.getName();
            if (identifier == null || identifier.isBlank()) {
                return null;
            }
            // Try by email first, then by loginId
            Optional<Employee> employeeOpt = employeeRepository.findFirstByEmailOrderByIdAsc(identifier);
            if (employeeOpt.isPresent()) {
                return employeeOpt.get();
            }
            return employeeRepository.findByLoginId(identifier).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to resolve current employee for permission check: {}", e.getMessage());
            return null;
        }
    }
}

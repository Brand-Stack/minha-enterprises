package com.app.billing.util;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.model.BaseEntity;
import com.app.billing.model.Employee;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AuditUtil {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * Get the current logged-in user's EmployeeCode (for createdBy/updatedBy).
     */
    public String getCurrentUser() {
        return resolveCurrentEmployee()
                .map(emp -> emp.getEmployeeCode() != null ? emp.getEmployeeCode() : emp.getEmployeeName())
                .orElseGet(this::getAuthFallbackName);
    }

    /**
     * Get the current logged-in user's display name for cumulative lastUpdatedBy trail.
     */
    public String getCurrentEmployeeName() {
        return resolveCurrentEmployee()
                .map(emp -> {
                    if (emp.getEmployeeName() != null && !emp.getEmployeeName().isBlank()) {
                        return emp.getEmployeeName().trim();
                    }
                    if (emp.getEmployeeCode() != null && !emp.getEmployeeCode().isBlank()) {
                        return emp.getEmployeeCode().trim();
                    }
                    return getAuthFallbackName();
                })
                .orElseGet(this::getAuthFallbackName);
    }

    private Optional<Employee> resolveCurrentEmployee() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return Optional.empty();
            }
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String employeeId = jwtUtil.extractClaim(token, claims -> (String) claims.get("employeeId"));
                    if (employeeId != null) {
                        return employeeRepository.findById(employeeId);
                    }
                } catch (Exception ignored) {
                    // Token parsing failed, fallback below
                }
            }
        } catch (Exception ignored) {
            // If any error occurs, return empty
        }
        return Optional.empty();
    }

    private String getAuthFallbackName() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "system";
    }

    /**
     * Append a name to the cumulative lastUpdatedBy trail, avoiding consecutive duplicates.
     */
    String appendLastUpdatedBy(String existing, String newName) {
        if (newName == null || newName.isBlank()) {
            return existing;
        }
        String trimmed = newName.trim();
        if (existing == null || existing.isBlank()) {
            return trimmed;
        }
        List<String> parts = Arrays.stream(existing.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        if (!parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase(trimmed)) {
            return String.join(", ", parts);
        }
        parts.add(trimmed);
        return String.join(", ", parts);
    }
    
    /**
     * Set audit fields for entity creation
     */
    public void setCreatedBy(BaseEntity entity) {
        String currentUser = getCurrentUser();
        String currentName = getCurrentEmployeeName();
        entity.setCreatedBy(currentUser);
        entity.setUpdatedBy(currentUser);
        entity.setLastUpdatedBy(currentName);
    }
    
    /**
     * Set audit fields for entity update
     */
    public void setUpdatedBy(BaseEntity entity) {
        String currentUser = getCurrentUser();
        String currentName = getCurrentEmployeeName();
        entity.setUpdatedBy(currentUser);
        entity.setLastUpdatedBy(appendLastUpdatedBy(entity.getLastUpdatedBy(), currentName));
    }
}


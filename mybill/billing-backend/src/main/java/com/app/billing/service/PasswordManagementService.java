package com.app.billing.service;

import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dto.ChangePasswordRequest;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Employee;
import com.app.billing.util.AuditUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordManagementService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditUtil auditUtil;
    private final AuditService auditService;

    /** Change the currently-authenticated user's own password. */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResourceNotFoundException("No authenticated user");
        }
        String loginId = auth.getName();
        Employee employee = employeeRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (employee.getPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), employee.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        employee.setPassword(passwordEncoder.encode(request.getNewPassword()));
        auditUtil.setUpdatedBy(employee);
        employeeRepository.save(employee);

        auditService.log("PASSWORD_CHANGE", "EMPLOYEES", employee.getId(), "User changed own password");
    }

    /** Admin reset of any employee's password (no current-password verification). */
    @Transactional
    public void resetPassword(String employeeId, String newPassword) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        employee.setPassword(passwordEncoder.encode(newPassword));
        auditUtil.setUpdatedBy(employee);
        employeeRepository.save(employee);

        auditService.log("PASSWORD_RESET", "EMPLOYEES", employeeId,
                "Admin reset password for employee " + employee.getEmployeeCode());
    }
}

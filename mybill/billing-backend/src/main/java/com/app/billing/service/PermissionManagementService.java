package com.app.billing.service;

import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.PermissionRequestRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.Employee;
import com.app.billing.model.PermissionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionManagementService {

    private final PermissionRequestRepository permissionRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanySettingsService companySettingsService;
    private final AttendanceCalculationEngine calculationEngine;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public PermissionRequest requestPermission(PermissionRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + request.getEmployeeId()));

        if (request.getDate() == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Date, Start Time, and End Time are required for permission request");
        }

        int duration = (int) Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        if (duration <= 0) {
            throw new IllegalArgumentException("End Time must be after Start Time");
        }

        request.setEmployeeCode(employee.getEmployeeCode());
        request.setEmployeeName(employee.getEmployeeName());
        request.setDurationMinutes(duration);
        request.setStatus(PermissionRequest.PermissionStatus.PENDING);

        PermissionRequest saved = permissionRepository.save(request);
        auditService.log("APPLY_PERMISSION", "PERMISSION_MGMT", saved.getId(),
                "Permission requested by " + employee.getEmployeeName() + " (" + duration + " minutes)");

        // Send notification to Admin/HR approvers
        try {
            notificationService.sendNotificationToApprovers(
                    com.app.billing.security.Modules.PERMISSION_MGMT,
                    com.app.billing.security.Modules.VIEW,
                    com.app.billing.model.Notification.NotificationType.PERMISSION_REQUEST_SUBMITTED,
                    "New Permission Request",
                    employee.getEmployeeName() + " has requested permission on " + request.getDate() + " from " + request.getStartTime() + " to " + request.getEndTime() + ".",
                    "PermissionRequest",
                    saved.getId(),
                    "/attendance/permissions",
                    com.app.billing.model.Notification.NotificationPriority.NORMAL,
                    java.util.Map.of("date", request.getDate().toString(), "startTime", request.getStartTime().toString(), "endTime", request.getEndTime().toString())
            );
        } catch (Exception e) {
            log.error("Failed to send permission submission notification: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional
    public PermissionRequest approveOrRejectPermission(String requestId, boolean approve, String adminRemarks, String adminUsername) {
        PermissionRequest request = permissionRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission request not found with ID: " + requestId));

        if (request.getStatus() != PermissionRequest.PermissionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING permission requests can be approved/rejected");
        }

        if (approve) {
            request.setStatus(PermissionRequest.PermissionStatus.APPROVED);
            request.setApprovedBy(adminUsername);
            request.setApprovedDate(LocalDateTime.now());
            request.setAdminRemarks(adminRemarks);

            // Recalculate attendance record for the date
            Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(request.getEmployeeId(), request.getDate());
            if (recOpt.isPresent()) {
                AttendanceRecord existing = recOpt.get();
                CompanySettingsDto settings = companySettingsService.getSettings();
                AttendanceRecord recalculated = calculationEngine.calculateDailyAttendance(
                        existing.getEmployeeId(),
                        existing.getEmployeeCode(),
                        existing.getEmployeeName(),
                        existing.getDepartment(),
                        existing.getDate(),
                        existing.getPunches(),
                        settings,
                        null,
                        request
                );
                recalculated.setId(existing.getId());
                attendanceRecordRepository.save(recalculated);
            }

            auditService.log("APPROVE_PERMISSION", "PERMISSION_MGMT", request.getId(), "Approved permission for " + request.getEmployeeName());
        } else {
            request.setStatus(PermissionRequest.PermissionStatus.REJECTED);
            request.setApprovedBy(adminUsername);
            request.setApprovedDate(LocalDateTime.now());
            request.setAdminRemarks(adminRemarks);
            auditService.log("REJECT_PERMISSION", "PERMISSION_MGMT", request.getId(), "Rejected permission for " + request.getEmployeeName());
        }

        PermissionRequest saved = permissionRepository.save(request);

        // Send notification to applicant employee
        try {
            com.app.billing.model.Notification.NotificationType nType = approve ? 
                    com.app.billing.model.Notification.NotificationType.PERMISSION_REQUEST_APPROVED : 
                    com.app.billing.model.Notification.NotificationType.PERMISSION_REQUEST_REJECTED;
            String nTitle = approve ? "Permission Request Approved" : "Permission Request Rejected";
            String nMsg = approve ? 
                    "Your permission request for " + request.getDate() + " from " + request.getStartTime() + " to " + request.getEndTime() + " has been approved." :
                    "Your permission request for " + request.getDate() + " from " + request.getStartTime() + " to " + request.getEndTime() + " has been rejected. Reason: " + (adminRemarks != null && !adminRemarks.isBlank() ? adminRemarks : "N/A");

            notificationService.sendNotification(
                    request.getEmployeeId(),
                    nType,
                    nTitle,
                    nMsg,
                    com.app.billing.security.Modules.PERMISSION_MGMT,
                    "PermissionRequest",
                    saved.getId(),
                    "/attendance/permissions",
                    com.app.billing.model.Notification.NotificationPriority.NORMAL,
                    java.util.Map.of("status", saved.getStatus().name(), "adminRemarks", adminRemarks != null ? adminRemarks : "")
            );
        } catch (Exception e) {
            log.error("Failed to send permission decision notification: {}", e.getMessage());
        }

        return saved;
    }

    public List<PermissionRequest> getPermissionHistoryForEmployee(String employeeId) {
        return permissionRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<PermissionRequest> getPendingPermissionRequests() {
        return permissionRepository.findByStatus(PermissionRequest.PermissionStatus.PENDING);
    }

    public List<PermissionRequest> getAllPermissionRequests() {
        return permissionRepository.findAll();
    }

    @Transactional
    public PermissionRequest updatePermission(String id, PermissionRequest updatedRequest) {
        PermissionRequest existing = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission request not found with ID: " + id));

        if (updatedRequest.getDate() != null) {
            existing.setDate(updatedRequest.getDate());
        }
        if (updatedRequest.getStartTime() != null) {
            existing.setStartTime(updatedRequest.getStartTime());
        }
        if (updatedRequest.getEndTime() != null) {
            existing.setEndTime(updatedRequest.getEndTime());
        }
        if (updatedRequest.getReason() != null) {
            existing.setReason(updatedRequest.getReason());
        }

        if (existing.getStartTime() != null && existing.getEndTime() != null) {
            int duration = (int) Duration.between(existing.getStartTime(), existing.getEndTime()).toMinutes();
            if (duration <= 0) {
                throw new IllegalArgumentException("End Time must be after Start Time");
            }
            existing.setDurationMinutes(duration);
        }

        PermissionRequest saved = permissionRepository.save(existing);
        auditService.log("UPDATE_PERMISSION", "PERMISSION_MGMT", saved.getId(), "Updated permission request ID: " + id);
        return saved;
    }

    @Transactional
    public void deletePermission(String id) {
        PermissionRequest existing = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission request not found with ID: " + id));

        permissionRepository.delete(existing);
        auditService.log("DELETE_PERMISSION", "PERMISSION_MGMT", id, "Deleted permission request for " + existing.getEmployeeName());
    }
}

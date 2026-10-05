package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "notifications")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Notification extends BaseEntity {

    @Indexed
    private String recipientEmployeeId;
    
    private String recipientEmail;

    private NotificationType type;
    
    private String title;
    
    private String message;

    @Indexed
    private String module; // LEAVE_MGMT, PERMISSION_MGMT, PAYROLL, SALARY_ADVANCE, ATTENDANCE_DEVICE, ATTENDANCE, etc.

    private String entityType; // LeaveRequest, PermissionRequest, SalaryAdvance, PayrollRecord, AttendanceDevice
    
    private String entityId;

    private String navigationTarget; // e.g. /attendance/leaves, /attendance/permissions, /attendance/payroll

    private NotificationPriority priority;

    @Indexed
    private boolean read;

    private LocalDateTime readAt;

    private Map<String, String> metadata;

    public enum NotificationType {
        LEAVE_REQUEST_SUBMITTED,
        LEAVE_REQUEST_APPROVED,
        LEAVE_REQUEST_REJECTED,
        PERMISSION_REQUEST_SUBMITTED,
        PERMISSION_REQUEST_APPROVED,
        PERMISSION_REQUEST_REJECTED,
        SALARY_ADVANCE_SUBMITTED,
        SALARY_ADVANCE_APPROVED,
        SALARY_ADVANCE_REJECTED,
        SALARY_ADVANCE_ADDED,
        SALARY_ADVANCE_REPAYMENT_RECORDED,
        OVERTIME_SUBMITTED,
        OVERTIME_APPROVED,
        OVERTIME_REJECTED,
        PAYSLIP_GENERATED,
        BONUS_ADDED,
        ATTENDANCE_EXCEPTION,
        ATTENDANCE_CORRECTION,
        DEVICE_SYNC_FAILURE,
        SYSTEM_EVENT
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
}

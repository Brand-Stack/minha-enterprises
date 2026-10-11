package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "leave_requests")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeaveRequest extends BaseEntity {
    @Indexed
    private String employeeId;
    private String employeeCode;
    private String employeeName;
    private String department;

    private LeaveType leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Double numberOfDays;
    private Boolean isHalfDay;
    private String reason;
    private String attachmentPath;

    @Indexed
    private LeaveStatus status;
    private LocalDate appliedDate;

    private String approvedBy;
    private LocalDateTime approvedDate;
    private String adminRemarks;

    public enum LeaveType {
        CASUAL_LEAVE,
        MEDICAL_LEAVE,
        EMERGENCY_LEAVE,
        COMP_OFF,
        OTHER_LEAVE,
        MATERNITY_LEAVE,
        PATERNITY_LEAVE,
        UNPAID_LOP
    }

    public enum LeaveStatus {
        DRAFT,
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}

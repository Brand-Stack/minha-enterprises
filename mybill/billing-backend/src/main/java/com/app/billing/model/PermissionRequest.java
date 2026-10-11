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
import java.time.LocalTime;

@Document(collection = "permission_requests")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PermissionRequest extends BaseEntity {
    @Indexed
    private String employeeId;
    private String employeeCode;
    private String employeeName;

    @Indexed
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private String reason;

    @Indexed
    private PermissionStatus status;

    private String approvedBy;
    private LocalDateTime approvedDate;
    private String adminRemarks;

    public enum PermissionStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}

package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "attendance_records")
@CompoundIndex(name = "emp_date_idx", def = "{'employeeId': 1, 'date': 1}", unique = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AttendanceRecord extends BaseEntity {
    private String employeeId;
    private String employeeCode;
    private String employeeName;
    private String department;
    
    @Indexed
    private LocalDate date;
    
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Double totalWorkingHours;
    private Double expectedWorkingHours;
    private Integer breakDurationMinutes;
    
    private Boolean lateArrival;
    private Integer lateMinutes;
    
    private Boolean earlyDeparture;
    private Integer earlyMinutes;
    
    private Double overtimeHours;
    
    private AttendanceStatus status;
    private AttendancePunch.PunchSource sourceOfPunch;
    private String remarks;
    
    @Builder.Default
    private List<AttendancePunch> punches = new ArrayList<>();
    
    // Audit of manual override / corrections
    private Boolean manuallyCorrected;
    private String correctedBy;
    private LocalDateTime correctedAt;
    private String correctionReason;
    private String originalValuesJson;

    public enum AttendanceStatus {
        PRESENT,
        ABSENT,
        LATE,
        EARLY_CHECKOUT,
        LATE_AND_EARLY_CHECKOUT,
        HALF_DAY,
        LEAVE,
        HOLIDAY,
        WEEK_OFF,
        PERMISSION,
        WORK_FROM_HOME,
        OVERTIME,
        INCOMPLETE
    }
}

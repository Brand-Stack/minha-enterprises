package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "overtime_records")
@CompoundIndex(name = "ot_emp_date_idx", def = "{'employeeId': 1, 'date': 1}", unique = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OvertimeRecord extends BaseEntity {
    private String employeeId;
    private String employeeCode;
    private String employeeName;
    private LocalDate date;

    private Double normalWorkingHours;
    private Double actualWorkingHours;
    private Double overtimeHours;

    private BigDecimal overtimeRatePerHour;
    private BigDecimal overtimeAmount;

    private OvertimeStatus status;
    private String approvedBy;
    private LocalDateTime approvedDate;
    private String remarks;

    public enum OvertimeStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}

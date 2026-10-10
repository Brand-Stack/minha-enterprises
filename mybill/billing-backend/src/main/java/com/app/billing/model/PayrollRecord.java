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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "payroll_records")
@CompoundIndex(name = "emp_month_idx", def = "{'employeeId': 1, 'payrollMonth': 1}", unique = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PayrollRecord extends BaseEntity {
    @Indexed
    private String payslipNumber; // e.g., PAY-202609-EMP001

    @Indexed
    private String employeeId;
    private String employeeCode;
    private String employeeName;
    private String department;
    private String designation;
    private LocalDate joiningDate;

    @Indexed
    private String payrollMonth; // YYYY-MM

    // Attendance stats
    private Integer totalWorkingDays;
    private Integer presentDays;
    private Integer leaveDays;
    private Double lopDays;
    private Double overtimeHours;

    // Earnings
    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal allowances;
    private BigDecimal overtimeAmount;
    // Bonus Breakdown
    private BigDecimal monthlyBonus;
    private BigDecimal performanceBonus;
    private BigDecimal festivalBonus;
    private BigDecimal yearlyBonusAmount;
    private BigDecimal specialBonus;
    private BigDecimal otherBonus;
    private BigDecimal bonusAmount; // Total bonus

    private BigDecimal otherEarnings;
    
    @Builder.Default
    private Map<String, BigDecimal> breakdownEarnings = new HashMap<>();

    // Deductions
    private BigDecimal pfAmount;
    private BigDecimal taxAmount;
    private BigDecimal professionalTax;
    private BigDecimal lopAmount;
    private BigDecimal advanceDeductionAmount;
    private BigDecimal otherDeductions;

    @Builder.Default
    private Map<String, BigDecimal> breakdownDeductions = new HashMap<>();

    // Salary Advance Snapshot (at payroll calculation/generation time)
    private String advanceId;
    private BigDecimal originalAdvanceAmount;
    private BigDecimal previousAdvanceBalance;
    private BigDecimal remainingAdvanceBalance;
    private BigDecimal totalAdvanceRecoveredSoFar;

    // Totals
    private BigDecimal grossSalary;
    private BigDecimal totalEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;

    // Bank Details Snapshot at Payslip Generation
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String bankBranch;

    // Status & Locking
    private PayrollStatus status;
    private Boolean isLocked;
    private LocalDateTime lockedAt;
    private String lockedBy;
    private Integer version;

    private LocalDateTime generatedAt;
    private String generatedBy;

    @Builder.Default
    private List<PayrollAuditEntry> auditLogs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayrollAuditEntry {
        private LocalDateTime timestamp;
        private String action; // e.g. EDIT, GENERATE, LOCK, UNLOCK
        private String modifiedBy;
        private String fieldName;
        private String oldValue;
        private String newValue;
        private String remarks;
    }

    public enum PayrollStatus {
        DRAFT,
        PENDING_REVIEW,
        APPROVED,
        PROCESSED,
        GENERATED,
        LOCKED,
        PAID,
        CANCELLED
    }
}

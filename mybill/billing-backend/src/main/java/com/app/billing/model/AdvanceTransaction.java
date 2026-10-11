package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "advance_transactions")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdvanceTransaction extends BaseEntity {

    @Indexed
    private String advanceAccountId;

    @Indexed
    private String employeeId;

    private String employeeCode;
    private String employeeName;

    @Indexed
    private TransactionType transactionType; // ADVANCE_GIVEN, REPAYMENT, PAYROLL_DEDUCTION, ADJUSTMENT

    private BigDecimal amount;

    @Indexed
    private LocalDate transactionDate;

    private String paymentMode; // Cash, GPay, PhonePe, UPI, Bank Transfer, Cheque, Other
    private String otherPaymentModeDetails; // Details when paymentMode is Other
    private String referenceNumber; // Transaction ID / Reference

    private String description;
    private String reason;
    private String remarks;
    private String notes;

    @Indexed
    private String payrollMonth; // YYYY-MM
    private String payslipId;

    private BigDecimal previousBalance;
    private BigDecimal resultingBalance;

    @Builder.Default
    private String status = "COMPLETED"; // COMPLETED, REVERSED, VOIDED

    public enum TransactionType {
        ADVANCE_GIVEN,
        REPAYMENT,
        PAYROLL_DEDUCTION,
        ADJUSTMENT
    }
}

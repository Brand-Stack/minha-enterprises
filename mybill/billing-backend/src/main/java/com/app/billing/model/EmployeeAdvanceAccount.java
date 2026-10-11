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

@Document(collection = "employee_advance_accounts")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeAdvanceAccount extends BaseEntity {

    @Indexed(unique = true)
    private String employeeId;

    private String employeeCode;
    private String employeeName;

    @Builder.Default
    private BigDecimal totalAdvanceGiven = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalRepaid = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalPayrollDeducted = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal outstandingBalance = BigDecimal.ZERO;

    @Builder.Default
    private String status = "ACTIVE";
}

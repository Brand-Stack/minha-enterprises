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
import java.util.HashMap;
import java.util.Map;

@Document(collection = "salary_structures")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SalaryStructure extends BaseEntity {
    @Indexed(unique = true)
    private String employeeId;
    private String employeeCode;
    private String employeeName;

    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal allowances;
    private BigDecimal pfDeduction;
    private BigDecimal professionalTax;
    private BigDecimal incomeTax;
    
    @Builder.Default
    private Map<String, BigDecimal> customEarnings = new HashMap<>();

    @Builder.Default
    private Map<String, BigDecimal> customDeductions = new HashMap<>();

    private LocalDate effectiveFrom;
    private Boolean active;
}

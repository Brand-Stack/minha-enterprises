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

@Document(collection = "employee_bonuses")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeBonus extends BaseEntity {
    @Indexed
    private String employeeId;
    private String employeeCode;
    private String employeeName;

    private BonusType bonusType;
    private BigDecimal amount;
    private Integer bonusYear;
    private LocalDate paymentDate;
    
    @Indexed
    private String payrollMonth; // YYYY-MM
    private String remarks;
    private String approvedBy;
    private LocalDateTime approvedDate;

    public enum BonusType {
        PERFORMANCE_BONUS,
        FESTIVAL_BONUS,
        SPECIAL_BONUS,
        YEARLY_BONUS,
        INCENTIVE,
        OTHER_BONUS
    }
}

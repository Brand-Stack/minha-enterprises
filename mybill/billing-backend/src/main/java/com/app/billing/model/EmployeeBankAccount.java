package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeBankAccount {
    private String id;
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String bankBranch;
    private String accountType; // SAVINGS, CURRENT, SALARY
    @Builder.Default
    private Boolean isPrimary = false;
    private LocalDateTime createdDate;
}

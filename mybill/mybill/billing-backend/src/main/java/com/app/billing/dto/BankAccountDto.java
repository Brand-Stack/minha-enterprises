package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountDto {
    private String id;
    private String accountName;
    private String accountNumber;
    private String bankName;
    private String branch;
    private String ifscCode;
}

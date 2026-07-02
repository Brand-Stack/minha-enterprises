package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Embedded bank account for Company Settings (RTGS/NEFT).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    private String id; // identity for list (e.g. index or UUID)

    private String accountName;
    private String accountNumber;
    private String bankName;
    private String branch;
    private String ifscCode;
}

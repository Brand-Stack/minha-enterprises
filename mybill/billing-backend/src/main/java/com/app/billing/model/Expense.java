package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(collection = "expenses")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Expense extends BaseEntity {
    private String expenseNumber;
    private LocalDate expenseDate;
    private String expenseDetails; // Expense description/details
    private String category; // Must be from Master (EXPENSE_CATEGORY)
    private String partyName; // Optional - for party-related expenses
    private String purchaseBillId; // Link to Purchase Bill if created from paid purchase
    private PaymentType paymentType; // CASH, ONLINE, CHEQUE
    private BigDecimal amount; // Total expense amount
    private BigDecimal paidCashAmount; // Cash paid (for Cash In Hand segregation)
    private BigDecimal paidOnlineAmount; // Online/digital paid (for Cash In Hand segregation)
    private String description;
    private TransactionType transactionType; // CASH_IN or CASH_OUT

    public TransactionType getTransactionType() {
        return transactionType == null ? TransactionType.CASH_IN : transactionType;
    }

    public enum PaymentType {
        CASH, ONLINE, CHEQUE
    }

    public enum TransactionType {
        CASH_IN, CASH_OUT
    }
}


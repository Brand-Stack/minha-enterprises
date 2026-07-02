package com.app.billing.dto;

import com.app.billing.model.Expense;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDto {
    private String id;
    private String expenseNumber;
    
    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;
    
    private String expenseDetails; // Expense description/details
    
    @NotBlank(message = "Category is required")
    private String category; // Must be from Master (EXPENSE_CATEGORY)
    
    private String partyName;
    private String purchaseBillId; // Link to Purchase Bill if created from paid purchase
    private Expense.PaymentType paymentType;
    
    @NotNull(message = "Amount is required")
    private BigDecimal amount; // Total expense amount
    
    private BigDecimal paidCashAmount; // Cash paid (for Cash In Hand segregation)
    private BigDecimal paidOnlineAmount; // Online/digital paid (for Cash In Hand segregation)
    
    private String description;
    private String lastUpdatedBy; // EmployeeCode of user who last updated
    private String transactionType; // CASH_IN or CASH_OUT
    private String createdBy; // EmployeeCode of user who created
}


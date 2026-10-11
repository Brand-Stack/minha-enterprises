package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashInHandDto {
    
    // Starting balance = closing balance at end of day before period start (e.g. yesterday's end for "Today")
    private BigDecimal startingLiquidCash;
    private BigDecimal startingOnlineBalance;
    private BigDecimal startingTotalBalance;
    
    // Closing balance = starting + period movement (never use totalAmount for cash flow)
    private BigDecimal liquidCash; // startingLiquidCash + (totalCashReceived - totalCashExpenses) in period
    private BigDecimal onlineBalance; // startingOnlineBalance + (totalOnlineReceived - totalOnlineExpenses) in period
    private BigDecimal totalAvailableBalance; // liquidCash + onlineBalance
    
    // For reports: Money IN breakdown (period only)
    private BigDecimal totalCashReceived; // SUM(receivedCashAmount) from Invoices
    private BigDecimal totalOnlineReceived; // SUM(receivedOnlineAmount) from Invoices
    private BigDecimal totalSalesReceived; // totalCashReceived + totalOnlineReceived
    
    // For reports: Money OUT breakdown
    private BigDecimal totalCashExpenses; // SUM(paidCashAmount) from Purchase + Expense
    private BigDecimal totalOnlineExpenses; // SUM(paidOnlineAmount) from Purchase + Expense
    private BigDecimal totalExpensesPaid; // totalCashExpenses + totalOnlineExpenses
    
    // Drill-down lists
    private List<SalesEntry> salesEntries;
    private List<ExpenseEntry> expenseEntries;
    
    // Filter information
    private LocalDate startDate;
    private LocalDate endDate;
    private String filterType; // DAY, WEEK, MONTH, YEAR, CUSTOM
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesEntry {
        private String id;
        private LocalDate date;
        private String moduleType; // GST_INVOICE, ESTIMATE
        private String partyName;
        private String billNumber;
        private BigDecimal itemsTotal;
        private BigDecimal receivedAmount; // receivedCashAmount + receivedOnlineAmount
        private BigDecimal receivedCashAmount;
        private BigDecimal receivedOnlineAmount;
        private BigDecimal pendingAmount;
        private String paymentStatus;
        private String paymentMode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseEntry {
        private String id;
        private LocalDate date;
        private String moduleType; // PURCHASE, EXPENSE
        private String partyName;
        private String billNumber;
        private BigDecimal itemsTotal;
        private BigDecimal paidAmount; // paidCashAmount + paidOnlineAmount
        private BigDecimal paidCashAmount;
        private BigDecimal paidOnlineAmount;
        private BigDecimal pendingAmount;
        private String paymentStatus;
        private String paymentType;
        private String category;
        private String transactionType;
    }
}

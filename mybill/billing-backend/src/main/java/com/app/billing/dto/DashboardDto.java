package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DashboardDto {
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TurnoverData {
        private String period; // Date or period label
        private LocalDate date;
        private BigDecimal amount;
        private Long invoiceCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingData {
        private String period;
        private LocalDate date;
        private Long totalInvoices;
        private Long estimateCount;
        private Long gstBillCount;
        private BigDecimal totalAmount;
        private BigDecimal estimateAmount;
        private BigDecimal gstAmount;
    }
    
    /** Invoice-level row for Transaction Report Excel (Date, Bill No, Party, Type, Total, Received, Pending, Status, Payment Mode) */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionReportRow {
        private LocalDate date;
        private String billNumber;
        private String partyName;
        private String billType;      // GST, ESTIMATE
        private BigDecimal totalAmount;
        private BigDecimal receivedAmount;
        private BigDecimal pendingAmount;
        private String paymentStatus; // PENDING, PARTIAL, PAID
        private String paymentMode;   // CASH, ONLINE, CHEQUE, PARTIAL
        private String status;       // NORMAL, DRAFT, CORRECTED, RETURNED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockData {
        private String itemCode;
        private String itemName;
        private BigDecimal currentStock;
        private BigDecimal minStockLevel;
        private String status; // LOW, OK, OUT_OF_STOCK
        private BigDecimal stockValue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesData {
        private String itemCode;
        private String itemName;
        private BigDecimal quantitySold;
        private BigDecimal totalRevenue;
        private Long invoiceCount;
        private String modeOfPayment; // CASH, ONLINE, CHEQUE, PARTIAL
        private String onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
        private BigDecimal paidAmount;
        private BigDecimal pendingAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseData {
        private String billNumber;
        private LocalDate billDate;
        private String partyName;
        private String itemCode;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
        private String paymentType; // CASH, ONLINE, CHEQUE
        private String paymentStatus; // PAID, UNPAID, PARTIAL
        private String onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
        private BigDecimal paidAmount;
        private BigDecimal outstandingAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartyData {
        private String partyId;
        private String partyName;
        private Long totalBills;
        private Long gstBills;
        private Long estimateBills;
        private BigDecimal totalAmount;
        private BigDecimal gstAmount;
        private BigDecimal estimateAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardSummary {
        private BigDecimal totalRevenue;
        private BigDecimal dailyRevenue;
        private BigDecimal monthlyRevenue;
        private BigDecimal yearlyRevenue;
        private Long totalInvoices;
        private Long monthlyInvoices;
        private Integer lowStockItems;
        private Integer outOfStockItems;
        private Long totalCustomers;
        private BigDecimal totalStockValue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashInHandData {
        private LocalDate date;
        private BigDecimal totalSales;
        private BigDecimal totalExpenses;
        private BigDecimal liquidCash;      // SUM(receivedCashAmount) - SUM(cashExpenses)
        private BigDecimal onlineBalance;   // SUM(receivedOnlineAmount) - SUM(onlineExpenses)
        private BigDecimal totalAvailableBalance; // liquidCash + onlineBalance
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseData {
        private String expenseNumber;
        private LocalDate expenseDate;
        private String category;
        private String partyName;
        private String paymentMode;
        private String onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
        private String onlinePaymentReference; // UPI ID or Phone Number
        private BigDecimal amount;
        private String description;
        private String expenseDetails;
        private Boolean isAutoGenerated; // true if auto-generated from Purchase
        private String transactionType; // CASH_IN, CASH_OUT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotationReportData {
        private String quotationNumber;
        private LocalDate quotationDate;
        private String partyName;
        private BigDecimal totalAmount;
        private String paymentMode; // CASH, ONLINE, CHEQUE, PARTIAL
        private String onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
        private BigDecimal paidAmount;
        private BigDecimal pendingAmount;
    }
}


package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Document(collection = "purchase_bills")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseBill extends BaseEntity {
    private String purchaseEntryNo; // Purchase Entry Number
    private String billNumber; // Purchase Invoice Number (mandatory)
    private LocalDate billDate;
    private String partyId;
    private String partyName;
    private String partyPhone;
    private List<PurchaseItem> items;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private PaymentType paymentType; // CASH, ONLINE, CHEQUE
    private PaymentStatus paymentStatus; // PAID, UNPAID, PARTIAL
    private OnlinePaymentMethod onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others (when paymentType is ONLINE)
    private String onlinePaymentReference; // UPI ID or Phone Number (optional)
    private BigDecimal paidAmount; // Amount paid (for partial payments) = paidCashAmount + paidOnlineAmount
    private BigDecimal outstandingAmount; // Remaining balance = totalAmount - paidAmount
    private BigDecimal paidCashAmount; // Cash paid (for Cash In Hand segregation)
    private BigDecimal paidOnlineAmount; // Online/digital paid (for Cash In Hand segregation)
    private String uploadedBillFile; // File path or URL
    private String notes;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseItem {
        private String itemId;
        private String itemCode;
        private String itemName;
        private String category; // From Master (ITEM_CATEGORY)
        private Integer quantity; // Integer only, no decimals
        private String unit; // From Item master, NOT dropdown
        private BigDecimal price;
        private Integer taxPercent; // Integer only, no decimals
        private BigDecimal taxAmount;
        private BigDecimal amount;
    }
    
    public enum PaymentType {
        CASH, ONLINE, CHEQUE
    }
    
    public enum PaymentStatus {
        PAID, UNPAID, PARTIAL
    }
    
    public enum OnlinePaymentMethod {
        GPAY, PHONEPE, PAYTM, OTHER_UPI, OTHERS
    }
}


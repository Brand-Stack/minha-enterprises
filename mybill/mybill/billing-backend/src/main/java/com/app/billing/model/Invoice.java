package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "invoices")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Invoice extends BaseEntity {
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDateTime invoiceDateTime; // Full timestamp with hours:minutes:seconds
    private String partyId;
    private String partyName;
    
    // Shipping Address (for GST Bill)
    private String shippingAddress;
    private String shippingCity;
    private String shippingState;
    private String shippingPincode;
    private Boolean sameAsPartyAddress; // If true, use party's address
    
    private List<InvoiceItem> items;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private BigDecimal paidAmount; // Amount already paid (for partial payments) = receivedCashAmount + receivedOnlineAmount
    private BigDecimal balanceAmount; // Remaining balance = totalAmount - paidAmount
    private BigDecimal receivedCashAmount; // Physical cash received (for Cash In Hand segregation)
    private BigDecimal receivedOnlineAmount; // UPI/Bank/digital received (for Cash In Hand segregation)
    private PaymentMode modeOfPayment; // CASH, ONLINE, CHEQUE, PARTIAL
    private OnlinePaymentMethod onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
    private String onlinePaymentReference; // UPI ID or Phone Number (optional)
    private String notes;
    // New fields for Estimate/GST billing
    private BillType billType; // ESTIMATE or GST
    private BillStatus status; // NORMAL, CORRECTED, RETURNED, or DRAFT
    private BigDecimal gstPercent; // GST percentage
    private BigDecimal cgst; // Central GST
    private BigDecimal sgst; // State GST
    // IGST removed - only CGST and SGST are used
    private List<AuditLogEntry> auditLog; // For tracking corrections
    private Boolean stockRestored; // Flag to track if stock has been restored (for returned invoices)
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogEntry {
        private java.time.LocalDateTime timestamp;
        private String userId;
        private String userName;
        private String description; // e.g., "Total amount corrected from X to Y"
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceItem {
        private String itemId;
        private String itemCode;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        // taxRate removed - GST is calculated from item's taxRate, not stored in invoice item
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
    }
    
    public enum PaymentStatus {
        PENDING, PARTIAL, PAID
    }
    
    public enum BillType {
        ESTIMATE, GST
    }
    
    public enum BillStatus {
        NORMAL, CORRECTED, RETURNED, DRAFT
    }
    
    public enum PaymentMode {
        CASH, ONLINE, CHEQUE, PARTIAL
    }
    
    public enum OnlinePaymentMethod {
        GPAY, PHONEPE, PAYTM, OTHER_UPI, OTHERS
    }
}


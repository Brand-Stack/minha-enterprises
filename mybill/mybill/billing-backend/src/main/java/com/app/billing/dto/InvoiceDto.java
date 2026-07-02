package com.app.billing.dto;

import com.app.billing.model.Invoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {
    private String id;
    private String invoiceNumber;
    
    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;
    
    private LocalDateTime invoiceDateTime; // Full timestamp with hours:minutes:seconds
    
    @NotBlank(message = "Party ID is required")
    private String partyId;
    
    private String partyName;
    
    // Shipping Address (for GST Bill)
    private String shippingAddress;
    private String shippingCity;
    private String shippingState;
    private String shippingPincode;
    private Boolean sameAsPartyAddress;
    
    @Valid
    @NotNull(message = "Items are required")
    private List<InvoiceItemDto> items;
    
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private Invoice.PaymentStatus paymentStatus;
    private BigDecimal paidAmount; // Amount already paid (for partial payments) = receivedCashAmount + receivedOnlineAmount
    private BigDecimal balanceAmount; // Remaining balance = totalAmount - paidAmount
    private BigDecimal receivedCashAmount; // Physical cash received (for Partial: user-entered)
    private BigDecimal receivedOnlineAmount; // UPI/Bank/digital received (for Partial: user-entered)
    private Invoice.PaymentMode modeOfPayment; // CASH, ONLINE, CHEQUE, PARTIAL
    private Invoice.OnlinePaymentMethod onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
    private String onlinePaymentReference; // UPI ID or Phone Number (optional)
    private String notes;
    // New fields for Estimate/GST billing
    private Invoice.BillType billType; // ESTIMATE or GST
    private Invoice.BillStatus status; // NORMAL, CORRECTED, RETURNED, or DRAFT
    private BigDecimal gstPercent; // GST percentage
    private BigDecimal cgst; // Central GST
    private BigDecimal sgst; // State GST
    // IGST removed - only CGST and SGST are used
    private String lastUpdatedBy; // EmployeeCode of user who last updated
    private List<Invoice.AuditLogEntry> auditLog; // For tracking corrections
    private Boolean stockRestored; // Flag to track if stock has been restored (for returned invoices)
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceItemDto {
        @NotBlank(message = "Item ID is required")
        private String itemId;
        
        private String itemCode;
        private String itemName;
        
        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;
        
        @NotNull(message = "Unit price is required")
        private BigDecimal unitPrice;
        
        // taxRate removed - GST is calculated from item's taxRate, not stored in invoice item
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
    }
}


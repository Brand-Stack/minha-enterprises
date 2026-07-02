package com.app.billing.dto;

import com.app.billing.model.PurchaseBill;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseBillDto {
    private String id;
    private String purchaseEntryNo; // Purchase Entry Number (optional)
    
    // Purchase Invoice Number is OPTIONAL - supplier's invoice number
    // Can be left blank if supplier doesn't provide invoice number
    private String billNumber;
    
    @NotNull(message = "Bill date is required")
    private LocalDate billDate;
    
    @NotBlank(message = "Party ID is required")
    private String partyId;
    
    private String partyName;
    private String partyPhone;
    
    @Valid
    @NotNull(message = "Items are required")
    private List<PurchaseItemDto> items;
    
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private PurchaseBill.PaymentType paymentType;
    private PurchaseBill.PaymentStatus paymentStatus; // PAID, UNPAID, PARTIAL
    private PurchaseBill.OnlinePaymentMethod onlinePaymentMethod; // GPay, PhonePe, Paytm, OtherUPI, Others
    private String onlinePaymentReference; // UPI ID or Phone Number (optional)
    private BigDecimal paidAmount; // Amount paid (for partial payments) = paidCashAmount + paidOnlineAmount
    private BigDecimal outstandingAmount; // Remaining balance = totalAmount - paidAmount
    private BigDecimal paidCashAmount; // Cash paid (for Cash In Hand segregation)
    private BigDecimal paidOnlineAmount; // Online/digital paid (for Cash In Hand segregation)
    private String uploadedBillFile;
    private String notes;
    private String lastUpdatedBy; // EmployeeCode of user who last updated
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseItemDto {
        @NotBlank(message = "Item ID is required")
        private String itemId;
        
        private String itemCode;
        private String itemName;
        private String category; // From Master (ITEM_CATEGORY)
        
        @NotNull(message = "Quantity is required")
        private Integer quantity; // Integer only, no decimals
        
        private String unit; // From Item master, read-only
        
        @NotNull(message = "Price is required")
        private BigDecimal price;
        
        @NotNull(message = "Tax percent is required")
        private Integer taxPercent; // Integer only, no decimals
        private BigDecimal taxAmount;
        private BigDecimal amount;
    }
}


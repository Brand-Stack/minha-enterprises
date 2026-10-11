package com.app.billing.dto;

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
public class QuotationDto {
    private String id;
    private String quotationNumber;
    
    @NotNull(message = "Quotation date is required")
    private LocalDate quotationDate;
    
    private LocalDateTime quotationDateTime;
    
    @NotBlank(message = "Party ID is required")
    private String partyId;
    
    private String partyName;
    
    // Shipping To section
    private String shippingToPartyId;
    private String shippingToPartyName;
    private String shippingAddress;
    private String shippingCity;
    private String shippingState;
    private String shippingPincode;
    private Boolean sameAsPartyAddress;
    
    // Delivery only (Quotation is non-ledger; no payment fields)
    private LocalDate deliveryDate;
    private Integer validTillDays;
    
    @Valid
    @NotNull(message = "Items are required")
    private List<QuotationItemDto> items;
    
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal taxAmount;
    private BigDecimal cgst;  // GST breakdown
    private BigDecimal sgst;  // GST breakdown
    private BigDecimal totalAmount;
    private String notes;
    private String lastUpdatedBy; // EmployeeCode of user who last updated
    
    // GST Option
    private Boolean gstRequired; // true = with GST, false = without GST
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotationItemDto {
        private String itemId;
        private String itemCode;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
    }
}


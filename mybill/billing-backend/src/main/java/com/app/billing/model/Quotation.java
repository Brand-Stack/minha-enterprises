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

@Document(collection = "quotations")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Quotation extends BaseEntity {
    private String quotationNumber;
    private LocalDate quotationDate;
    private LocalDateTime quotationDateTime;
    private String partyId;
    private String partyName;
    
    // Shipping To section
    private String shippingToPartyId; // Optional - if different from partyId
    private String shippingToPartyName;
    private String shippingAddress;
    private String shippingCity;
    private String shippingState;
    private String shippingPincode;
    private Boolean sameAsPartyAddress; // If true, auto-populate from party address
    
    // Delivery only (Quotation is non-ledger; no payment fields)
    private LocalDate deliveryDate;
    private Integer validTillDays; // Number of days quotation is valid
    
    private List<QuotationItem> items;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal taxAmount;
    private BigDecimal cgst;  // GST breakdown
    private BigDecimal sgst;  // GST breakdown
    private BigDecimal totalAmount;
    private String notes;
    
    // GST Option
    private Boolean gstRequired; // true = with GST, false = without GST
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotationItem {
        private String itemId;
        private String itemCode;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
    }
}


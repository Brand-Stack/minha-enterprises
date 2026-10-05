package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Onboard Quotation for prospective customers with custom user-defined slabs.
 * Completely isolated from operational calculations (Client Entry, Cash Booking, etc.).
 */
@Document(collection = "onboard_quotations")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OnboardQuotation extends BaseEntity {

    @Indexed
    private String quotationNumber;
    
    @Indexed
    private String customerName; // Prospective customer / company name
    private String branchName;
    private LocalDate effectiveDate;
    private LocalDate validTillDate;
    private String remarks;
    
    private CourierQuotation.QuotationStatus status;

    /** Fuel charge percentage applied on courier amount (e.g. 15.0 for 15%) */
    private Double fuelChargePercentage;

    /** FOV (Fragile/Own Value) charges */
    private Double fovCharges;

    /** Selected bank account ID for document footer */
    private String selectedBankAccountId;

    /** List of custom slabs defined by the user (Slab A, Slab B, Slab C, etc.) */
    @Builder.Default
    private List<OnboardSlab> slabs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnboardSlab {
        private String slabId;
        private String slabName; // User-configurable name (e.g. "Slab A - Economy", "Slab B")
        
        @Builder.Default
        private Boolean selected = true; // Included in generated PDF/Excel document

        @Builder.Default
        private List<ZoneRateConfig> zoneRates = new ArrayList<>();
    }
}

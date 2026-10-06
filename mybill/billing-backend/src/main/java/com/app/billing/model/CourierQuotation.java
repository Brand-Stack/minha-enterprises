package com.app.billing.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

/**
 * Courier Rate Quotation with FIXED zone/column structure.
 *
 * Rate map key convention:
 * Section 1 – Express + Surface (zones Z1–Z10):
 * Z{n}_E1, Z{n}_E2, Z{n}_E3 → Express cols
 * Z{n}_S1, Z{n}_S2 → Surface cols
 * Section 2 – Priority + Safety Plus (zones P1–P5):
 * P{n}_PC1, P{n}_PC2, P{n}_PC3 → Priority Class cols
 * P{n}_SP1, P{n}_SP2 → Safety Plus cols
 *
 * Values are stored as Strings to support numeric amounts as well as
 * special values like "NA" and "NOSERVICE".
 */
@Document(collection = "courier_quotations")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CourierQuotation extends BaseEntity {

    @Indexed
    private String quotationNumber;
    private String customerId;
    @Indexed
    private String customerName;
    private String branchName;
    private LocalDate effectiveDate;
    private LocalDate validTillDate;
    private String remarks;
    private QuotationStatus status;

    /** Rate type: EXPRESS_RATE, SURFACE_RATE, SAFETY_PLUS, PRIORITY_CLASS. Drives calculation and zone dropdown. */
    private String rateType;

    /** Fuel charge percentage applied on courier amount (e.g. 15.0 for 15%) */
    private Double fuelChargePercentage;

    /** FOV (Fragile/Own Value) charges - configurable like Fuel Charge %; shown in quotation PDF. */
    private Double fovCharges;

    /** Selected bank account ID (from Company Settings bankAccounts list) for invoice RTGS/NEFT. */
    private String selectedBankAccountId;

    /**
     * List of configured zone rates representing explicit numeric values based
     * on the dynamic zone configuration from the admin panel.
     */
    @Builder.Default
    private List<ZoneRateConfig> zoneRates = new ArrayList<>();

    public enum QuotationStatus {
        DRAFT, APPROVED, ACTIVE, EXPIRED
    }

    /** Rate type constants for zone filtering and calculation. */
    public static final String RATE_TYPE_EXPRESS = "EXPRESS_RATE";
    public static final String RATE_TYPE_SURFACE = "SURFACE_RATE";
    public static final String RATE_TYPE_SAFETY_PLUS = "SafetyPlus";
    public static final String RATE_TYPE_PRIORITY_CLASS = "PriorityClass";
}

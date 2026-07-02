package com.app.billing.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "monthly_courier_entries")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MonthlyCourierEntry extends BaseEntity {

    @Indexed
    private String monthlyQuotationId;

    private LocalDate entryDate;
    private String consignor;
    /** Receiver / customer name at destination. */
    private String receiverName;
    /** 6-digit India pincode. */
    @Indexed
    private String pincode;
    /** Locality / area (from pincode API or manual). */
    private String areaName;
    private String state;
    private String destinationCity;
    /** Full street address (optional; complements area/city). */
    private String fullAddress;
    /** Legacy combined destination line; kept for search and older rows. */
    @Indexed
    private String consigneeAddress;
    private String courierType; // e.g. FRANCH, ST, PROFESSIONAL
    private Double weight;
    /** AWB / tracking number; indexed for list search. */
    @Indexed
    private String trackingNumber;
    private String itemType; // e.g. DOCUMENT, PARCEL
    private String deliveryStatus; // e.g. Delivered, RTO

    private String zone; // e.g. Z1, Z2, Z3, P1
    /** Rate set for this entry: EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass. Drives amount calculation. */
    private String rateType;
    private Double rate; // Optional flat rate override
    private Double amount;
    /** Optional payment / amount status (Cash, GPay, COD, etc.) — aligns with collection & cash booking. */
    private String amountStatus;
    private Boolean amountOverridden; // True if amount was manually changed
    /** Optional additional charges for this shipment. Added to amount for entry total. */
    private Double additionalCharges;
    /** Optional description when additional charges are provided. */
    private String additionalChargesDescription;

    /**
     * When false, this line is excluded from GST in invoice/report grand total; null defaults to applicable.
     */
    private Boolean gstApplicable;
    /** When false, excluded from fuel surcharge allocation for that line; null defaults to applicable. */
    private Boolean fuelApplicable;
    /** When false, excluded from FOV allocation for that line; null defaults to applicable. */
    private Boolean fovApplicable;
}

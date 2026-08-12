package com.app.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCourierEntryDto {

    private String id;
    private String monthlyQuotationId;

    @NotNull(message = "Entry Date is required")
    private LocalDate entryDate;

    private String consignor;

    private String receiverName;

    private String pincode;

    private String areaName;
    private String state;
    private String destinationCity;

    private String fullAddress;

    /** Legacy single-line destination; optional if structured fields are used. */
    private String consigneeAddress;

    @NotBlank(message = "Courier Type is required")
    private String courierType;

    @NotNull(message = "Weight is required")
    private Double weight;
    private String trackingNumber;
    private String itemType;
    private String deliveryStatus;

    @NotBlank(message = "Zone is required")
    private String zone;
    /** Rate set: EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass. Used for amount calculation. */
    private String rateType;
    private Double rate;
    private Double amount;
    /** Optional: Cash, GPay, Pending, COD, Paid, UnPaid, or custom — used for reporting/dashboards. */
    private String amountStatus;
    private Boolean amountOverridden;
    /** Optional additional charges for this shipment. Entry total = amount + additionalCharges. */
    private Double additionalCharges;
    private String additionalChargesDescription;

    /** False = no GST on this line for invoice-style totals; null = apply. */
    private Boolean gstApplicable;
    /** False = no fuel on this line; null = apply. */
    private Boolean fuelApplicable;
    /** False = no FOV on this line; null = apply. */
    private Boolean fovApplicable;

    // Audit
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastUpdatedBy;
}

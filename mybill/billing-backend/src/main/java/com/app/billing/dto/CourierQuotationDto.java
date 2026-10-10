package com.app.billing.dto;

import com.app.billing.model.CourierQuotation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierQuotationDto {

    private String id;
    private String quotationNumber;

    @NotBlank(message = "Customer ID is required")
    private String customerId;
    private String customerName;
    private String branchName;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    @NotNull(message = "Valid till date is required")
    private LocalDate validTillDate;

    private String remarks;
    private CourierQuotation.QuotationStatus status;

    /** Rate type: EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass */
    private String rateType;

    /** Fuel charge percentage applied on courier amount (e.g. 15.0 for 15%) */
    private Double fuelChargePercentage;

    /** FOV (Fragile/Own Value) charges - configurable; shown in quotation PDF. */
    private Double fovCharges;

    /** Selected bank account ID for invoice RTGS/NEFT */
    private String selectedBankAccountId;

    /**
     * List of configured zone rates representing explicit numeric values based
     * on the dynamic zone configuration from the admin panel.
     */
    private List<ZoneRateConfigDto> zoneRates = new ArrayList<>();

    // Audit
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastUpdatedBy;
}

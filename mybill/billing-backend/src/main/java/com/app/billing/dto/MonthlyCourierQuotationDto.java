package com.app.billing.dto;

import com.app.billing.model.MonthlyCourierQuotation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCourierQuotationDto {

    private String id;
    private String shopId;

    private String customerId;
    private String customerName;

    private String title;

    @NotBlank(message = "Month is required")
    private String month;

    @NotNull(message = "Year is required")
    private Integer year;

    private String zone;
    /** Format: 0001/2026-27. Editable. */
    private String invoiceNumber;
    private LocalDate invoiceDate;

    private Integer totalShipments;
    private Double totalWeight;
    private Double totalAmount;

    /** Internal notes (UI only, not in invoice/breakup). */
    private String note;

    /** Client-entry fuel % override; blank uses courier quotation. */
    private Double fuelChargePercentage;
    /** Client-entry FOV % override; blank uses courier quotation. */
    private Double fovCharges;
    /** Client-entry GST % override; blank uses company default then 0%. */
    private Double gstPercentage;
    private Boolean includeFuel;
    private Boolean includeGst;
    private Boolean includeFov;

    private String amountStatus;
    private String description;
    private Boolean isDownloaded;
    private List<MonthlyCourierQuotation.InvoiceActivityLog> invoiceActivityLogs = new ArrayList<>();

    // Audit
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastUpdatedBy;
}

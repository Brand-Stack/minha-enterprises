package com.app.billing.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "small_SMALL_CLIENT_ENTRY_quotations")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SmallClientEntryQuotation extends BaseEntity {

    private String shopId;
    private String customerId;
    private String customerName;
    private String title;
    private String month; // e.g. "MAY"
    private Integer year; // e.g. 2023

    /**
     * Sort key for list views: {@code year * 100 + monthIndex} (month 1â€“12). Maintained on create/update.
     */
    private Integer periodSort;

    private String zone;
    /** Invoice number format: 0001/2026-27 (running number / financial year). Editable. */
    private String invoiceNumber;
    /** Invoice date. Editable. Used to derive financial year when auto-generating invoice number. */
    private LocalDate invoiceDate;

    private Integer totalShipments;
    private Double totalWeight;
    private Double totalAmount;

    /** Internal remarks/notes. UI only; not shown on invoice or breakup PDF/Excel. */
    private String note;

    /** Status for Courier Report: Pending, Paid, Partial. */
    private String amountStatus;

    /** Extra description for Courier Report. */
    private String description;

    /** Client-entry override for fuel %; when set, takes priority over courier quotation. */
    private Double fuelChargePercentage;
    /** Client-entry override for FOV %; when set, takes priority over courier quotation. */
    private Double fovCharges;
    /** Client-entry GST % override; blank uses company default then 0%. */
    private Double gstPercentage;
    /** When false, fuel is excluded from invoice/report totals. Null defaults to true. */
    private Boolean includeFuel;
    /** When false, GST is excluded from invoice/report totals. Null defaults to true. */
    private Boolean includeGst;
    /** When false, FOV is excluded from invoice/report totals. Null defaults to true. */
    private Boolean includeFov;

    /** Tracks if the invoice has been downloaded. True = show in Courier Report. */
    private Boolean isDownloaded;

    /** Append-only invoice activity history for view, print, and download actions. */
    private List<InvoiceActivityLog> invoiceActivityLogs = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class InvoiceActivityLog {
        private String action;
        private LocalDateTime activityAt;

        public InvoiceActivityLog(String action, LocalDateTime activityAt) {
            this.action = action;
            this.activityAt = activityAt;
        }
    }
}


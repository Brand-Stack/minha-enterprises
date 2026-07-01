package com.app.billing.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MonthlyCourierEmailRequestDto {
    /** Email type: MONTHLY (full quotation) or DAILY (shipment for selected date only). */
    private String emailType;

    /** List of email addresses to send to. */
    private java.util.List<String> toAddresses;

    /** Attachment format: PDF or EXCEL. */
    private String attachmentFormat;

    /** Optional from-date filter for shipment breakup (MONTHLY + EXCEL). */
    private LocalDate fromDate;

    /** Optional to-date filter for shipment breakup (MONTHLY + EXCEL). */
    private LocalDate toDate;

    /** Specific date for DAILY email (required when emailType=DAILY). */
    private LocalDate specificDate;

    /**
     * When set (non-null), only these entry IDs are included in the email (after date filters).
     * For DAILY workflow: must be non-empty when user selects rows; empty list is invalid.
     */
    private java.util.List<String> selectedEntryIds;

    /** Include amount column in Excel. Default true. */
    private Boolean includeAmountInShipmentBreakup = true;

    /** Include GST and Fuel in Quotation. Default true. (Used when includeGst/includeFuel not set.) */
    private Boolean includeGstAndFuel = true;

    /** Include GST in amounts. Default true. */
    private Boolean includeGst = true;

    /** Include Fuel in amounts. Default true. */
    private Boolean includeFuel = true;

    /** Include Weight column in shipment breakup (PDF/Excel). Default true. */
    private Boolean includeWeight = true;

    private Boolean includeInvoice = true;
    private Boolean includeShipmentBreakup = true;
}

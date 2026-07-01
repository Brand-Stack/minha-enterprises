package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwbShipmentLookupDto {
    private boolean found;
    private String message;
    private String clientName;
    private String receiverName;
    private String receiverAddress;
    private String status;
    private Double weight;
    private Double amount;
    private LocalDate bookingDate;
    /** Optional; null if not tracked in system */
    private LocalDate expectedDeliveryDate;
    /** Monthly courier entry id (for deep links). */
    private String entryId;
    /** Parent monthly quotation id (navigate to client-entries/edit/{id}). */
    private String monthlyQuotationId;
    /** Normalized AWB / tracking number. */
    private String trackingNumber;
}

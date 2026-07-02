package com.app.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AwbCenterBulkGenerateRequest {
    @NotBlank
    private String startAwb;
    @NotBlank
    private String endAwb;
    /** When true, generated AWBs are mapped for Collection Center only. */
    private Boolean collectionCustomerAwb;
    private String collectionCustomerId;
    private String collectionCustomerName;
    /** Courier type from Company Settings shipment dropdown. */
    private String courierType;
}

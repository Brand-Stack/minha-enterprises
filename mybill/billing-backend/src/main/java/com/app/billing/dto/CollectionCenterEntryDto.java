package com.app.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCenterEntryDto {
    private String id;
    @NotBlank(message = "Collection customer is required")
    private String collectionCustomerId;
    private String customerName;
    private LocalDate entryDate;
    /** January … December — aligns with Client Entry month. */
    private String entryMonth;
    private Integer entryYear;
    /** When creating/updating from a PENDING registry row, pass its id (not persisted on entry). */
    private String consumeRegistryAwbId;
    private String consignor;
    private String receiverName;
    private String pincode;
    private String state;
    /** City / district from pincode lookup when available. */
    private String city;
    private String areaName;
    private String courier;
    private Double weight;
    private String awbNo;
    private String item;
    private String status;
    private Double amount;
    /** Paid | Pending | UnPaid | CashOnDelivery */
    private String amountStatus;
    private String remarks;
    private String lastUpdatedBy;
}

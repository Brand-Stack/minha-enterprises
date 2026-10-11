package com.app.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCustomerDto {
    private String id;
    private String customerCode;
    @NotBlank(message = "Customer name is required")
    private String customerName;
    private String contactPerson;
    private String email;
    private String phone;
    private String whatsappNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String gstin;
    private String areaName;
    /** When false, customer was soft-deleted. */
    private Boolean active;
    private String lastUpdatedBy;
}

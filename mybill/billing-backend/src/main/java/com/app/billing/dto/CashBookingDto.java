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
public class CashBookingDto {
    private String id;
    private LocalDate bookingDate;
    @NotBlank(message = "Receiver name is required")
    private String receiverName;
    @NotBlank(message = "Pincode is required")
    private String pincode;
    private String state;
    private String city;
    private String areaName;
    private String fullAddress;
    private String awbNo;
    private String courier;
    private Double weight;
    private String item;
    private String status;
    private Double amount;
    private Double receivedAmount;
    private Double pendingAmount;
    private String amountStatus;
    private String paymentMode;
    private String otherPaymentMode;
    @Builder.Default
    private java.util.List<String> fromPhoneNumbers = new java.util.ArrayList<>();
    @Builder.Default
    private java.util.List<String> toPhoneNumbers = new java.util.ArrayList<>();
    private String fromPhone;
    private String toPhone;
    private String remarks;
    private String lastUpdatedBy;
}

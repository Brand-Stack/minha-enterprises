package com.app.billing.dto;

import com.app.billing.model.PaymentOut;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOutDto {
    private String id;
    private String receiptNumber;
    
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    @NotBlank(message = "Party ID is required")
    private String partyId;
    
    private String partyName;
    private PaymentOut.PaymentType paymentType;
    
    @NotNull(message = "Paid amount is required")
    private BigDecimal paidAmount;
    
    private String description;
    private String uploadedBillFile;
    private String referenceNumber;
}


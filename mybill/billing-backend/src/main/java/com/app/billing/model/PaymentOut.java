package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(collection = "payment_out")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentOut extends BaseEntity {
    private String receiptNumber;
    private LocalDate date;
    private String partyId;
    private String partyName;
    private PaymentType paymentType; // CASH, ONLINE
    private BigDecimal paidAmount;
    private String description;
    private String uploadedBillFile; // File path or URL
    private String referenceNumber;
    
    public enum PaymentType {
        CASH, ONLINE
    }
}


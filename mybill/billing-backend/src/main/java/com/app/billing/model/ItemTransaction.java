package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(collection = "item_transactions")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ItemTransaction extends BaseEntity {
    private String transactionNumber;
    private LocalDate transactionDate;
    private String itemId;
    private String itemCode;
    private String itemName;
    private TransactionType transactionType;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String referenceId; // Invoice ID or other reference
    private String notes;
    
    public enum TransactionType {
        INWARD, OUTWARD
    }
}


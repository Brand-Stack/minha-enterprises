package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(collection = "accounting_entries")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountingEntry extends BaseEntity {

    public static final String TYPE_IN = "IN";
    public static final String TYPE_OUT = "OUT";

    @Indexed
    private LocalDate entryDate;

    /** IN or OUT */
    @Indexed
    private String entryType;

    private BigDecimal amount;

    private String description;

    /** Running balance after this row (non-negative). */
    private BigDecimal balanceAfter;
}

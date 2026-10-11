package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "invoice_sequence_counters")
public class InvoiceSequenceCounter {
    @Id
    private String id;
    /** Financial year start year, e.g. "2026" for FY 2026-27. */
    private String fy;
    private int lastSequence;
}

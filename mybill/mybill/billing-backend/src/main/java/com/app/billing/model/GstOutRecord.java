package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

@Document(collection = "gst_out_records")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GstOutRecord extends BaseEntity {
    private String invoiceNumber;
    private String courierType;
    private Double billingAmount;
    private Double cgst;
    private Double sgst;
    private Double gstTotal;
    private String month;
    private Integer year;
}

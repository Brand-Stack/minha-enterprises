package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "gst_in_reports")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GstInReport extends BaseEntity {
    private String month;
    private Integer year;
    private List<GstInDetailsRecord> records;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GstInDetailsRecord {
        private int serialNo;
        private String clientName;
        private double billingAmount;
        private double cgst;
        private double sgst;
        private double gstTotal;
    }
}

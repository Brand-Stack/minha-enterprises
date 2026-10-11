package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "collection_center_entries")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionCenterEntry extends BaseEntity {

    @Indexed
    private String collectionCustomerId;

    /** Denormalized from {@link CollectionCustomer} for reporting/search. */
    private String customerName;

    private LocalDate entryDate;

    /** Billing / grouping period (same naming as Client Entry header month). */
    private String entryMonth;
    private Integer entryYear;

    private String consignor;
    private String receiverName;
    private String pincode;
    private String state;
    /** City / district from pincode lookup when available. */
    private String city;
    private String areaName;
    private String courier;
    private Double weight;

    /** AWB — unique across client entry + collection center when present. */
    @Indexed(sparse = true)
    private String awbNo;

    private String item;
    private String status;
    private Double amount;

    private Double receivedAmount;
    private Double pendingAmount;

    @Indexed
    private String amountStatus;

    @Indexed
    private String paymentMode;

    private String otherPaymentMode;

    @Builder.Default
    private java.util.List<String> fromPhoneNumbers = new java.util.ArrayList<>();

    @Builder.Default
    private java.util.List<String> toPhoneNumbers = new java.util.ArrayList<>();

    public String getFromPhone() {
        return fromPhoneNumbers != null && !fromPhoneNumbers.isEmpty() ? String.join(", ", fromPhoneNumbers) : null;
    }

    public void setFromPhone(String phone) {
        if (phone != null && !phone.isBlank()) {
            this.fromPhoneNumbers = java.util.Arrays.asList(phone.split("\\s*,\\s*"));
        }
    }

    public String getToPhone() {
        return toPhoneNumbers != null && !toPhoneNumbers.isEmpty() ? String.join(", ", toPhoneNumbers) : null;
    }

    public void setToPhone(String phone) {
        if (phone != null && !phone.isBlank()) {
            this.toPhoneNumbers = java.util.Arrays.asList(phone.split("\\s*,\\s*"));
        }
    }

    private String remarks;
}

package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Manual / direct courier bookings (separate from Client Entry and Collection Entry).
 */
@Document(collection = "cash_bookings")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CashBooking extends BaseEntity {

    @Indexed
    private LocalDate bookingDate;

    private String receiverName;

    private String pincode;
    private String state;
    private String city;
    private String areaName;
    private String fullAddress;

    @Indexed(sparse = true)
    private String awbNo;

    private String courier;
    private Double weight;
    private String item;
    private String status;
    private Double amount;

    @Indexed
    private String amountStatus;

    private String remarks;
}

package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Parent entity for collection center shipments; mirrors client master fields. */
@Document(collection = "collection_customers")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionCustomer extends BaseEntity {

    @Indexed
    private String customerCode;
    private String customerName;
    private String contactPerson;
    private String email;
    private String phone;
    private String whatsappNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String gstin;

    /** Service / delivery area label from pincode lookup (optional). */
    private String areaName;

    /** Soft delete: when false, customer is hidden from lists and cannot be used for new entries. */
    private Boolean active;
}

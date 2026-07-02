package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

/** Customer / supplier master record. Stored in collection {@code parties} for backward compatibility. */
@Document(collection = "parties")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Client extends BaseEntity {
    private String partyCode;
    private String partyName;
    private String contactPerson;
    private String email;
    private String phone;
    private String whatsappNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String gstin;
    private ClientType partyType;

    public enum ClientType {
        CUSTOMER, SUPPLIER, BOTH
    }
}

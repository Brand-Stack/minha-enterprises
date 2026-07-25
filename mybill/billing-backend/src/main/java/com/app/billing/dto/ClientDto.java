package com.app.billing.dto;

import com.app.billing.model.Client;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientDto {
    private String id;

    @JsonAlias("partyCode")
    private String partyCode;

    @NotBlank(message = "Client name is required")
    @JsonAlias("partyName")
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
    private Client.ClientType partyType;
    private String lastUpdatedBy;
}

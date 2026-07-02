package com.app.billing.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AwbValidateRequestDto {
    private List<String> awbNumbers = new ArrayList<>();
    /** When editing a customer, AWBs already on this customer's registry are treated as allowed. */
    private String collectionCustomerId;
}

package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwbGlobalSearchHitDto {

    /** CLIENT_ENTRY | COLLECTION_CENTER | CASH_BOOKING */
    private String moduleCode;
    /** Display label for UI badge */
    private String moduleLabel;
    private String awbNo;
    private String receiverName;
    /** Consignor / customer / client name depending on module */
    private String customerOrConsignor;
    private LocalDate entryDate;
    private String courierType;
    private String status;
    private Double amount;
    /** Angular route without app base, e.g. /client-entries/edit/qid */
    private String path;
    /** Query params for navigation (entryId, awb, etc.) */
    private java.util.Map<String, String> queryParams;
}

package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCustomerAwbDto {
    private String id;
    private String collectionCustomerId;
    private String customerName;
    private String awbNo;
    private String status;
    private String collectionCenterEntryId;
    private LocalDateTime createdAt;
}

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
public class AwbCenterRecordDto {
    private String id;
    private String awbNumber;
    private String queueStatus;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String usedInModule;
    private String usedInReferenceId;
    private Boolean collectionCustomerAwb;
    private String collectionCustomerId;
    private String collectionCustomerName;
    private String courierType;
    private String lastUpdatedBy;
}

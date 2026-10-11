package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Collection-customer AWB metrics — separate from standard courier-type cards. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwbCollectionSummaryDto {
    private long totalCount;
    private long mappedCount;
    private long unmappedCount;
    private long pendingCount;
    private long completedCount;
}

package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwbCourierTypeStatsDto {
    private String courierType;
    private long pendingCount;
    private long completedCount;
    private long totalCount;
}

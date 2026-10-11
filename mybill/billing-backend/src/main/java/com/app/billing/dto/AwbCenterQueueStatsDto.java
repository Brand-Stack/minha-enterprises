package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwbCenterQueueStatsDto {
    private long pendingCount;
    private long completedCount;
    private long totalCount;

    /** Per-courier pending/completed/total (all AWBs with that courier type). */
    @Builder.Default
    private List<AwbCourierTypeStatsDto> courierTypeStats = new ArrayList<>();

    /** Collection-customer AWB summary — not mixed with courier-type cards. */
    private AwbCollectionSummaryDto collectionSummary;

    /** @deprecated use courierTypeStats */
    @Builder.Default
    private Map<String, Long> courierTypeCounts = new LinkedHashMap<>();

    /** @deprecated use collectionSummary */
    private long standardPendingCount;
    private long standardCompletedCount;
    private long standardTotalCount;
    private long collectionPendingCount;
    private long collectionCompletedCount;
    private long collectionTotalCount;
    private long collectionMappedCount;
    private long collectionUnmappedCount;
}

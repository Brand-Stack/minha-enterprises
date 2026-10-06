package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Courier dashboard + optional customer collection analytics. */
public class CourierDashboardDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        /** Client-entry rows on the current calendar day (Asia/Kolkata). */
        private Long bookingsToday;
        /** Client-entry count for the active dashboard scope (same as {@link #bookingsToday} when no date range). */
        private Long clientBookingsCount;
        /** Client + collection + cash booking rows in the active scope. */
        private Long shipmentsAllModulesCount;
        private Long delivered;
        private Long inTransit;
        private Long pending;
        private Long cancelled;
        private Long failedDeliveries;
        private BigDecimal totalRevenue;
        private BigDecimal monthlyRevenue;
        private BigDecimal codPending;
        /** Cash bookings count (respects revenue date range when {@link #revenueRangeActive} is true). */
        private Long cashBookingsCount;
        private BigDecimal cashBookingRevenue;
        /** Cash bookings with delivered-like status in the same scope as {@link #cashBookingsCount}. */
        private Long deliveredCashBookings;
        /** True when dashboard client/collection revenue cards use {@code revenueFrom}–{@code revenueTo}. */
        private Boolean revenueRangeActive;
    }

    /** Aggregated KPIs for a module within {@code from}–{@code to} (inclusive). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleRangeKpi {
        private LocalDate from;
        private LocalDate to;
        private Long totalBookings;
        private BigDecimal cashAmount;
        private BigDecimal gpayAmount;
        private BigDecimal pendingAmount;
        private BigDecimal codAmount;
        /** Client-entry total revenue (amount sum); null for non–client-entry modules. */
        private BigDecimal revenueTotal;
        private BigDecimal baseRevenue;
        private BigDecimal fuelCharges;
        private BigDecimal gstAmount;
        private BigDecimal fovAmount;
        /** Dynamic payment mode breakdown: e.g. {"Cash": 500.0, "GPAY": 1200.0, "PhonePe": 800.0, ...} */
        private java.util.Map<String, BigDecimal> paymentModeAmounts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleAwbGroup {
        /** Client / customer / consignor label. */
        private String name;
        /** Shipments / rows in scope for this label (full filter; not paginated). */
        private long totalCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPoint {
        private LocalDate date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyRevenuePoint {
        private String monthKey;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusSlice {
        private String status;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientRevenue {
        private String clientName;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityRow {
        private String awb;
        private String clientName;
        private String receiver;
        private String status;
        private LocalDate entryDate;
        private Double amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FullPayload {
        private Summary summary;
        private LocalDate revenueFrom;
        private LocalDate revenueTo;
        @Builder.Default
        private List<DailyPoint> dailyBookingTrend = new ArrayList<>();
        @Builder.Default
        private List<MonthlyRevenuePoint> monthlyRevenueTrend = new ArrayList<>();
        @Builder.Default
        private List<StatusSlice> statusDistribution = new ArrayList<>();
        @Builder.Default
        private List<ClientRevenue> topClientsByRevenue = new ArrayList<>();
        @Builder.Default
        private List<ActivityRow> latestDelivered = new ArrayList<>();
        @Builder.Default
        private List<ActivityRow> failedDeliveries = new ArrayList<>();
        private CollectionDashboard collection;
        private Long pendingCollectionAwbs;
        private AccountingSummaryDto accounting;
        @Builder.Default
        private List<CashFlowDailyDto> cashFlowTrend = new ArrayList<>();
        /** Collection center — amount buckets by {@link com.app.billing.util.AmountStatusBucketUtil}. */
        private ModuleRangeKpi collectionCenterKpi;
        private ModuleRangeKpi cashBookingKpi;
        private ModuleRangeKpi clientEntryKpi;
        @Builder.Default
        private List<ModuleAwbGroup> clientEntryAwbGroups = new ArrayList<>();
        @Builder.Default
        private List<ModuleAwbGroup> collectionCenterAwbGroups = new ArrayList<>();
        @Builder.Default
        private List<ModuleAwbGroup> cashBookingAwbGroups = new ArrayList<>();
        private ModuleRangeKpi smallClientEntryKpi;
        @Builder.Default
        private List<ModuleAwbGroup> smallClientEntryAwbGroups = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionSummary {
        private long totalEntries;
        private long paidCollections;
        private long pendingCollections;
        private long codCollections;
        private BigDecimal totalCollectionAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionDailyPoint {
        private LocalDate date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionAmountSlice {
        private String amountStatus;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionCustomerTop {
        private String customerName;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionMonthTrend {
        private String monthKey;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionDashboard {
        private CollectionSummary summary;
        @Builder.Default
        private List<CollectionDailyPoint> dailyCollectionTrend = new ArrayList<>();
        @Builder.Default
        private List<CollectionAmountSlice> amountStatusDistribution = new ArrayList<>();
        @Builder.Default
        private List<CollectionCustomerTop> topCollectionCustomers = new ArrayList<>();
        @Builder.Default
        private List<CollectionMonthTrend> monthlyCollectionTrend = new ArrayList<>();
    }
}

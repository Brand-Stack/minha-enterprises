package com.app.billing.security;

/**
 * Canonical card/section keys used for granular dashboard entitlement (SHOW / HIDE)
 * under the {@link Modules#DASHBOARD} module's {@code dashboardCards} map.
 *
 * <p>These keys are the single source of truth shared by:
 * <ul>
 *   <li>the module registry seed (so the Access Management UI can render checkboxes),</li>
 *   <li>the backend payload filter ({@code CourierDashboardService}), and</li>
 *   <li>the frontend dashboard card/section visibility checks.</li>
 * </ul>
 */
public final class DashboardCardKeys {

    private DashboardCardKeys() {
    }

    // Section 1 - Revenue & Shipments
    public static final String VIEW_CLIENT_BOOKINGS_RANGE = "VIEW_CLIENT_BOOKINGS_RANGE";
    public static final String VIEW_SHIPMENTS_ALL_MODULES = "VIEW_SHIPMENTS_ALL_MODULES";
    public static final String VIEW_DELIVERED = "VIEW_DELIVERED";
    public static final String VIEW_IN_TRANSIT = "VIEW_IN_TRANSIT";
    public static final String VIEW_PENDING = "VIEW_PENDING";
    public static final String VIEW_CLIENT_REVENUE = "VIEW_CLIENT_REVENUE";
    public static final String VIEW_COD_PENDING = "VIEW_COD_PENDING";

    // Section 2 - Collection Center
    public static final String VIEW_CC_TOTAL_BOOKINGS = "VIEW_CC_TOTAL_BOOKINGS";
    public static final String VIEW_CC_CASH = "VIEW_CC_CASH";
    public static final String VIEW_CC_GPAY = "VIEW_CC_GPAY";
    public static final String VIEW_CC_PENDING = "VIEW_CC_PENDING";
    public static final String VIEW_CC_COD = "VIEW_CC_COD";
    public static final String VIEW_CC_TOTAL_AMOUNT = "VIEW_CC_TOTAL_AMOUNT";

    // Section 3 - Cash Booking
    public static final String VIEW_CB_TOTAL_BOOKINGS = "VIEW_CB_TOTAL_BOOKINGS";
    public static final String VIEW_CB_CASH_AMOUNT = "VIEW_CB_CASH_AMOUNT";
    public static final String VIEW_CB_ONLINE_AMOUNT = "VIEW_CB_ONLINE_AMOUNT";
    public static final String VIEW_CB_PENDING = "VIEW_CB_PENDING";
    public static final String VIEW_CB_COD = "VIEW_CB_COD";
    public static final String VIEW_CB_TOTAL_AMOUNT = "VIEW_CB_TOTAL_AMOUNT";

    // Section 4 - Client Entry
    public static final String VIEW_CE_TOTAL_BOOKINGS = "VIEW_CE_TOTAL_BOOKINGS";
    public static final String VIEW_CE_CASH = "VIEW_CE_CASH";
    public static final String VIEW_CE_GPAY = "VIEW_CE_GPAY";
    public static final String VIEW_CE_PENDING = "VIEW_CE_PENDING";
    public static final String VIEW_CE_COD = "VIEW_CE_COD";
    public static final String VIEW_CE_BASE_REVENUE = "VIEW_CE_BASE_REVENUE";
    public static final String VIEW_CE_FUEL_CHARGES = "VIEW_CE_FUEL_CHARGES";
    public static final String VIEW_CE_GST_AMOUNT = "VIEW_CE_GST_AMOUNT";
    public static final String VIEW_CE_TOTAL_REVENUE = "VIEW_CE_TOTAL_REVENUE";

    // Section 5 - Small Client Entry
    public static final String VIEW_SCE_TOTAL_BOOKINGS = "VIEW_SCE_TOTAL_BOOKINGS";
    public static final String VIEW_SCE_CASH = "VIEW_SCE_CASH";
    public static final String VIEW_SCE_GPAY = "VIEW_SCE_GPAY";
    public static final String VIEW_SCE_TOTAL_REVENUE = "VIEW_SCE_TOTAL_REVENUE";

    // Section 6 - Customer / Client Summary
    public static final String VIEW_CLIENT_ENTRY_SUMMARY = "VIEW_CLIENT_ENTRY_SUMMARY";
    public static final String VIEW_SMALL_CLIENT_ENTRY_SUMMARY = "VIEW_SMALL_CLIENT_ENTRY_SUMMARY";
    public static final String VIEW_COLLECTION_CENTER_SUMMARY = "VIEW_COLLECTION_CENTER_SUMMARY";
    public static final String VIEW_CASH_BOOKING_SUMMARY = "VIEW_CASH_BOOKING_SUMMARY";

    // Section 7 - Ledger Snapshot
    public static final String VIEW_LEDGER_IN = "VIEW_LEDGER_IN";
    public static final String VIEW_LEDGER_OUT = "VIEW_LEDGER_OUT";
    public static final String VIEW_LEDGER_NET = "VIEW_LEDGER_NET";

    // Section 8 - Charts
    public static final String VIEW_CHART_DAILY_BOOKING_TREND = "VIEW_CHART_DAILY_BOOKING_TREND";
    public static final String VIEW_CHART_STATUS_DISTRIBUTION = "VIEW_CHART_STATUS_DISTRIBUTION";
    public static final String VIEW_CHART_CASH_FLOW_TREND = "VIEW_CHART_CASH_FLOW_TREND";
    public static final String VIEW_CHART_DAILY_COLLECTION_TREND = "VIEW_CHART_DAILY_COLLECTION_TREND";
    public static final String VIEW_CHART_COLLECTION_AMOUNT_STATUS = "VIEW_CHART_COLLECTION_AMOUNT_STATUS";
    public static final String VIEW_CHART_COLLECTION_TREND_BY_MONTH = "VIEW_CHART_COLLECTION_TREND_BY_MONTH";

    // Section 9 - Attendance & Payroll
    public static final String VIEW_ATTENDANCE_PAYROLL_SECTION = "VIEW_ATTENDANCE_PAYROLL_SECTION";
}

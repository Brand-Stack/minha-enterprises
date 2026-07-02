package com.app.billing.security;

/**
 * Central registry of module keys and standard action constants used by the
 * {@code @RequiresPermission} annotation and the metadata-driven module registry.
 *
 * <p>Keeping these as constants ensures controllers, the registry seed and the
 * permission evaluator all reference identical keys.</p>
 */
public final class Modules {

    private Modules() {
    }

    // Module keys
    public static final String DASHBOARD = "DASHBOARD";
    public static final String CLIENTS = "CLIENTS";
    public static final String SMALL_CLIENTS = "SMALL_CLIENTS";
    public static final String COLLECTION_CUSTOMER = "COLLECTION_CUSTOMER";
    public static final String CASH_BOOKING = "CASH_BOOKING";
    public static final String ITEMS = "ITEMS";
    public static final String AWB_CENTER = "AWB_CENTER";
    public static final String MASTER_DATA = "MASTER_DATA";
    public static final String ZONE_CONFIG = "ZONE_CONFIG";
    public static final String COURIER_QUOTATION = "COURIER_QUOTATION";
    public static final String QUOTATION = "QUOTATION";
    public static final String CLIENT_ENTRY = "CLIENT_ENTRY";
    public static final String SMALL_CLIENT_ENTRY = "SMALL_CLIENT_ENTRY";
    public static final String COLLECTION_CENTER = "COLLECTION_CENTER";
    public static final String BILLING = "BILLING";
    public static final String INVOICE = "INVOICE";
    public static final String ACCOUNTING = "ACCOUNTING";
    public static final String PURCHASE_BILLS = "PURCHASE_BILLS";
    public static final String PAYMENT_OUT = "PAYMENT_OUT";
    public static final String EXPENSES = "EXPENSES";
    public static final String CASH_IN_HAND = "CASH_IN_HAND";
    public static final String EMPLOYEES = "EMPLOYEES";
    public static final String REPORTS = "REPORTS";
    public static final String SETTINGS = "SETTINGS";
    public static final String ACCESS_CONTROL = "ACCESS_CONTROL";
    public static final String ENTITLEMENT_MGMT = "ENTITLEMENT_MGMT";
    public static final String GST_REPORT = "GST_REPORT";

    // Standard actions
    public static final String VIEW = "view";
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String DELETE = "delete";
    public static final String EXPORT = "export";
    public static final String PRINT = "print";
    public static final String EMAIL = "email";
    public static final String DOWNLOAD = "download";
}

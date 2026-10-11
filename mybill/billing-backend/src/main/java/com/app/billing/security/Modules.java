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
    public static final String EMPLOYEES = "EMPLOYEES";
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
    public static final String ONBOARD_QUOTATION = "ONBOARD_QUOTATION";
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
    // Attendance & Payroll Modules (7 canonical modules)
    public static final String MY_ATTENDANCE = "MY_ATTENDANCE";
    public static final String MASTER_ATTENDANCE = "MASTER_ATTENDANCE";
    public static final String LEAVES = "LEAVES";
    public static final String PERMISSIONS = "PERMISSIONS";
    public static final String PAYROLL_PAYSLIPS = "PAYROLL_PAYSLIPS";
    public static final String BIOMETRIC_DEVICES = "BIOMETRIC_DEVICES";
    public static final String ATTENDANCE_CONFIG = "ATTENDANCE_CONFIG";
    public static final String WORKING_HOURS_CONFIG = "ATTENDANCE_CONFIG";

    // Legacy HR keys mapped for backward compatibility
    public static final String ATTENDANCE = "MASTER_ATTENDANCE";
    public static final String LEAVE_MGMT = "LEAVES";
    public static final String PERMISSION_MGMT = "PERMISSIONS";
    public static final String OVERTIME = "MASTER_ATTENDANCE";
    public static final String PAYROLL = "PAYROLL_PAYSLIPS";
    public static final String SALARY_ADVANCE = "PAYROLL_PAYSLIPS";
    public static final String BONUS = "PAYROLL_PAYSLIPS";
    public static final String ATTENDANCE_DEVICE = "BIOMETRIC_DEVICES";
    public static final String REPORTS = "REPORTS";
    public static final String SETTINGS = "SETTINGS";
    public static final String ACCESS_CONTROL = "ACCESS_CONTROL";
    public static final String ENTITLEMENT_MGMT = "ENTITLEMENT_MGMT";
    public static final String GST_REPORT = "GST_REPORT";
    public static final String CLIENT_ENTRY_REPORT = "CLIENT_ENTRY_REPORT";
    public static final String SMALL_CLIENT_ENTRY_REPORT = "SMALL_CLIENT_ENTRY_REPORT";

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

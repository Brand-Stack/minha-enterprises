package com.app.billing.service;

import com.app.billing.dao.ModuleRegistryRepository;
import com.app.billing.model.ModuleRegistry;
import com.app.billing.model.ModuleRegistry.ModuleAction;
import com.app.billing.security.DashboardCardKeys;
import com.app.billing.security.Modules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Metadata-driven module registry. The full catalogue of securable modules is declared
 * statically in {@link #definitions()}. On startup the registry is synced to MongoDB so
 * that any newly-added module automatically appears in Entitlement Management without
 * further code changes to the permission engine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleRegistryService {

    private static final List<String> CRUD = List.of("view", "create", "edit", "delete");
    private static final List<String> CRUD_EXPORT = List.of("view", "create", "edit", "delete", "export");
    private static final List<String> FULL = List.of("view", "create", "edit", "delete", "export", "print", "email", "download");

    private final ModuleRegistryRepository moduleRegistryRepository;

    /**
     * Insert any missing module definitions and refresh metadata for existing ones.
     * Existing permission assignments are unaffected because they live in the
     * {@code entitlements} collection, not here.
     */
    public void syncRegistry() {
        List<ModuleRegistry> definitions = definitions();
        java.util.Set<String> validKeys = definitions.stream()
                .map(ModuleRegistry::getModuleKey)
                .collect(java.util.stream.Collectors.toSet());

        // Purge any orphan/legacy entries from MongoDB that are no longer in standard definitions
        List<ModuleRegistry> existingList = moduleRegistryRepository.findAll();
        for (ModuleRegistry existing : existingList) {
            if (!validKeys.contains(existing.getModuleKey())) {
                moduleRegistryRepository.delete(existing);
                log.info("Purged obsolete module registry entry: {}", existing.getModuleKey());
            }
        }

        int created = 0;
        for (ModuleRegistry def : definitions) {
            Optional<ModuleRegistry> existing = moduleRegistryRepository.findByModuleKey(def.getModuleKey());
            if (existing.isPresent()) {
                ModuleRegistry current = existing.get();
                current.setDisplayName(def.getDisplayName());
                current.setParent(def.getParent());
                current.setSortOrder(def.getSortOrder());
                current.setOperations(def.getOperations());
                current.setActions(def.getActions());
                current.setFields(def.getFields());
                current.setDashboardCards(def.getDashboardCards());
                current.setDashboard(def.isDashboard());
                moduleRegistryRepository.save(current);
            } else {
                moduleRegistryRepository.save(def);
                created++;
            }
        }
        log.info("Module registry synced: {} definitions, {} newly registered", definitions.size(), created);
    }

    public List<ModuleRegistry> findAll() {
        return moduleRegistryRepository.findAllByOrderBySortOrderAsc();
    }

    private static ModuleAction action(String key, String name) {
        return ModuleAction.builder().key(key).displayName(name).build();
    }

    @SafeVarargs
    private static List<ModuleAction> actions(ModuleAction... a) {
        return new ArrayList<>(List.of(a));
    }

    /**
     * Static catalogue of all securable modules. Add a new entry here to expose a new
     * module in Entitlement Management; no other change to the permission system is needed.
     */
    private List<ModuleRegistry> definitions() {
        List<ModuleRegistry> list = new ArrayList<>();
        int order = 0;

        // ---- GENERAL ----
        list.add(ModuleRegistry.builder()
                .moduleKey("DASHBOARD").displayName("Dashboard").parent("GENERAL").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view")))
                .dashboard(true)
                .dashboardCards(actions(
                        // Section 1 - Revenue & Shipments
                        action(DashboardCardKeys.VIEW_CLIENT_BOOKINGS_RANGE, "Revenue & Shipments: View Client Bookings (Range)"),
                        action(DashboardCardKeys.VIEW_SHIPMENTS_ALL_MODULES, "Revenue & Shipments: View Shipments (All Modules)"),
                        action(DashboardCardKeys.VIEW_DELIVERED, "Revenue & Shipments: View Delivered"),
                        action(DashboardCardKeys.VIEW_IN_TRANSIT, "Revenue & Shipments: View In Transit"),
                        action(DashboardCardKeys.VIEW_PENDING, "Revenue & Shipments: View Pending"),
                        action(DashboardCardKeys.VIEW_CLIENT_REVENUE, "Revenue & Shipments: View Client Revenue"),
                        action(DashboardCardKeys.VIEW_COD_PENDING, "Revenue & Shipments: View COD Pending"),
                        // Section 2 - Collection Center
                        action(DashboardCardKeys.VIEW_CC_TOTAL_BOOKINGS, "Collection Center: View Total Bookings"),
                        action(DashboardCardKeys.VIEW_CC_CASH, "Collection Center: View Cash"),
                        action(DashboardCardKeys.VIEW_CC_GPAY, "Collection Center: View GPay"),
                        action(DashboardCardKeys.VIEW_CC_PENDING, "Collection Center: View Pending"),
                        action(DashboardCardKeys.VIEW_CC_COD, "Collection Center: View COD"),
                        action(DashboardCardKeys.VIEW_CC_TOTAL_AMOUNT, "Collection Center: View Total Amount (Cash + GPay)"),
                        // Section 3 - Cash Booking
                        action(DashboardCardKeys.VIEW_CB_TOTAL_BOOKINGS, "Cash Booking: View Total Bookings"),
                        action(DashboardCardKeys.VIEW_CB_CASH_AMOUNT, "Cash Booking: View Cash Amount"),
                        action(DashboardCardKeys.VIEW_CB_ONLINE_AMOUNT, "Cash Booking: View Online Amount"),
                        action(DashboardCardKeys.VIEW_CB_PENDING, "Cash Booking: View Pending"),
                        action(DashboardCardKeys.VIEW_CB_COD, "Cash Booking: View COD"),
                        action(DashboardCardKeys.VIEW_CB_TOTAL_AMOUNT, "Cash Booking: View Total Amount (Cash + Online)"),
                        // Section 4 - Client Entry
                        action(DashboardCardKeys.VIEW_CE_TOTAL_BOOKINGS, "Client Entry: View Total Bookings"),
                        action(DashboardCardKeys.VIEW_CE_CASH, "Client Entry: View Cash"),
                        action(DashboardCardKeys.VIEW_CE_GPAY, "Client Entry: View GPay"),
                        action(DashboardCardKeys.VIEW_CE_PENDING, "Client Entry: View Pending"),
                        action(DashboardCardKeys.VIEW_CE_COD, "Client Entry: View COD"),
                        action(DashboardCardKeys.VIEW_CE_BASE_REVENUE, "Client Entry: View Base Revenue"),
                        action(DashboardCardKeys.VIEW_CE_FUEL_CHARGES, "Client Entry: View Fuel Charges"),
                        action(DashboardCardKeys.VIEW_CE_GST_AMOUNT, "Client Entry: View GST Amount"),
                        action(DashboardCardKeys.VIEW_CE_TOTAL_REVENUE, "Client Entry: View Total Revenue"),
                        // Section 5 - Small Client Entry
                        action(DashboardCardKeys.VIEW_SCE_TOTAL_BOOKINGS, "Small Client Entry: View Total Bookings"),
                        action(DashboardCardKeys.VIEW_SCE_CASH, "Small Client Entry: View Cash"),
                        action(DashboardCardKeys.VIEW_SCE_GPAY, "Small Client Entry: View GPay"),
                        action(DashboardCardKeys.VIEW_SCE_TOTAL_REVENUE, "Small Client Entry: View Total Revenue"),
                        // Section 6 - Customer / Client Summary
                        action(DashboardCardKeys.VIEW_CLIENT_ENTRY_SUMMARY, "Summary: View Client Entry Summary"),
                        action(DashboardCardKeys.VIEW_SMALL_CLIENT_ENTRY_SUMMARY, "Summary: View Small Client Entry Summary"),
                        action(DashboardCardKeys.VIEW_COLLECTION_CENTER_SUMMARY, "Summary: View Collection Center Summary"),
                        action(DashboardCardKeys.VIEW_CASH_BOOKING_SUMMARY, "Summary: View Cash Booking Summary"),
                        // Section 7 - Ledger Snapshot
                        action(DashboardCardKeys.VIEW_LEDGER_IN, "Ledger Snapshot: View IN Amount"),
                        action(DashboardCardKeys.VIEW_LEDGER_OUT, "Ledger Snapshot: View OUT Amount"),
                        action(DashboardCardKeys.VIEW_LEDGER_NET, "Ledger Snapshot: View Net Amount"),
                        // Section 8 - Charts
                        action(DashboardCardKeys.VIEW_CHART_DAILY_BOOKING_TREND, "Charts: View Daily Booking Trend"),
                        action(DashboardCardKeys.VIEW_CHART_STATUS_DISTRIBUTION, "Charts: View Status Distribution"),
                        action(DashboardCardKeys.VIEW_CHART_CASH_FLOW_TREND, "Charts: View Cash Flow Trend"),
                        action(DashboardCardKeys.VIEW_CHART_DAILY_COLLECTION_TREND, "Charts: View Daily Collection Trend"),
                        action(DashboardCardKeys.VIEW_CHART_COLLECTION_AMOUNT_STATUS, "Charts: View Collection Amount Status"),
                        action(DashboardCardKeys.VIEW_CHART_COLLECTION_TREND_BY_MONTH, "Charts: View Collection Trend By Month"),
                        // Section 9 - Attendance & Payroll
                        action(DashboardCardKeys.VIEW_ATTENDANCE_PAYROLL_SECTION, "HR: View Attendance & Payroll Management Section")))
                .build());
        list.add(ModuleRegistry.builder()
                .moduleKey("CLIENTS").displayName("Clients").parent("GENERAL").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("SMALL_CLIENTS").displayName("Small Clients").parent("GENERAL").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("COLLECTION_CUSTOMER").displayName("Collection Customer").parent("GENERAL").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT))
                .actions(actions(action("CLEAR_PENDING_AWBS", "Clear Pending AWBs"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("CASH_BOOKING").displayName("Cash Booking").parent("GENERAL").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());

        // ---- MASTER ----
        list.add(ModuleRegistry.builder()
                .moduleKey("MASTER_DATA").displayName("Create Category").parent("MASTER").sortOrder(order++)
                .operations(new ArrayList<>(CRUD)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("ITEMS").displayName("Items").parent("MASTER").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("AWB_CENTER").displayName("AWB Center").parent("MASTER").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view", "export")))
                .actions(actions(
                        action("GENERATE_BULK_AWBS", "Generate Bulk AWBs"),
                        action("DELETE_AWBS", "Delete AWBs"),
                        action("EXPORT_AWBS", "Export AWBs"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("ZONE_CONFIG").displayName("Zone Configurations").parent("MASTER").sortOrder(order++)
                .operations(new ArrayList<>(CRUD)).build());

        // ---- QUOTATIONS ----
        list.add(ModuleRegistry.builder()
                .moduleKey("COURIER_QUOTATION").displayName("Courier Quotations").parent("QUOTATIONS").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("QUOTATION").displayName("Quotations").parent("QUOTATIONS").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("ONBOARD_QUOTATION").displayName("Onboard Quotation").parent("QUOTATIONS").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());

        // ---- BILLING ----
        list.add(ModuleRegistry.builder()
                .moduleKey("CLIENT_ENTRY").displayName("Client Entry").parent("BILLING").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("ADD_ENTRY", "Add Entry"),
                        action("EDIT_ENTRY", "Edit Entry"),
                        action("DELETE_ENTRY", "Delete Entry"),
                        action("SEND_MAIL", "Send Mail"),
                        action("DOWNLOAD_PDF", "Download PDF"),
                        action("DOWNLOAD_EXCEL", "Download Excel"),
                        action("PRINT_INVOICE", "Print Invoice"),
                        action("VIEW_INVOICE", "View Invoice"),
                        action("EXPORT_REPORT", "Export Report")))
                .fields(actions(
                        action("amount", "Amount"),
                        action("revenue", "Revenue"),
                        action("gst", "GST"),
                        action("fuelCharges", "Fuel Charges"),
                        action("profit", "Profit"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("SMALL_CLIENT_ENTRY").displayName("Small Client Entry").parent("BILLING").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("COLLECTION_CENTER").displayName("Collection Center").parent("BILLING").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("ADD", "Add"),
                        action("EDIT", "Edit"),
                        action("DELETE", "Delete"),
                        action("CLEAR_PENDING_AWBS", "Clear Pending AWBs"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("BILLING").displayName("Billing").parent("BILLING").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());

        // ---- ACCOUNTS ----
        list.add(ModuleRegistry.builder()
                .moduleKey("ACCOUNTING").displayName("Accounting").parent("ACCOUNTS").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT))
                .actions(actions(
                        action("ADD_IN", "Add IN"),
                        action("ADD_OUT", "Add OUT"),
                        action("EDIT_ENTRY", "Edit Entry"),
                        action("DELETE_ENTRY", "Delete Entry")))
                .fields(actions(
                        action("balance", "Accounting Balance"),
                        action("profit", "Profit"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("PURCHASE_BILLS").displayName("Purchase").parent("ACCOUNTS").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("PAYMENT_OUT").displayName("Payment Out").parent("ACCOUNTS").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("EXPENSES").displayName("Cash In").parent("ACCOUNTS").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT)).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("CASH_IN_HAND").displayName("Cash In Hand").parent("ACCOUNTS").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view", "export", "download")))
                .fields(actions(action("balance", "Cash Balance"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey("GST_REPORT").displayName("GST Report").parent("ACCOUNTS").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view")))
                .actions(actions(
                        action("view_gst_in", "View GST IN"),
                        action("generate_gst_in", "Generate GST Report"),
                        action("regenerate_gst_in", "Regenerate GST Report"),
                        action("view_gst_out", "View GST OUT"),
                        action("add_gst_out", "Add GST OUT"),
                        action("edit_gst_out", "Edit GST OUT"),
                        action("delete_gst_out", "Delete GST OUT"),
                        action("view_ledger", "View GST Ledger"),
                        action("export_excel", "Export Excel"),
                        action("export_pdf", "Export PDF"),
                        action("print", "Print GST Report")
                )).build());

        // ---- HR ----
        list.add(ModuleRegistry.builder()
                .moduleKey("EMPLOYEES").displayName("Employees").parent("ACCOUNTING SOLUTIONS").sortOrder(order++)
                .operations(new ArrayList<>(CRUD_EXPORT))
                .fields(actions(
                        action("bankDetails", "Bank Details Access"),
                        action("salary", "Salary Structure Access"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.MY_ATTENDANCE).displayName("My Attendance").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("PUNCH_IN", "Punch In"),
                        action("PUNCH_OUT", "Punch Out"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.MASTER_ATTENDANCE).displayName("Master Attendance").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("MANAGE_ATTENDANCE", "Manage All Attendance"),
                        action("CORRECT_ATTENDANCE", "Update Attendance"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.LEAVES).displayName("Leaves").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("APPLY_LEAVE", "Apply Leave"),
                        action("APPROVE_LEAVE", "Approve / Reject Leave"),
                        action("MANAGE_LEAVE_ENTITLEMENT", "Manage Leave Entitlement"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.PERMISSIONS).displayName("Permissions").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("APPLY_PERMISSION", "Apply Permission"),
                        action("APPROVE_PERMISSION", "Approve / Reject Permission"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.PAYROLL_PAYSLIPS).displayName("Payroll & Payslips").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("MANAGE_SALARY_STRUCTURE", "Manage Salary Structure"),
                        action("PROCESS_PAYROLL", "Process Monthly Payroll"),
                        action("GENERATE_PAYSLIP", "Generate Payslip PDF"),
                        action("REQUEST_ADVANCE", "Request Advance"),
                        action("APPROVE_ADVANCE", "Approve Advance"),
                        action("ADD_BONUS", "Add Bonus"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.BIOMETRIC_DEVICES).displayName("Biometric Devices").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL))
                .actions(actions(
                        action("SYNC_DEVICE", "Sync Device"),
                        action("MAP_EMPLOYEE", "Map Employee to Device"))).build());

        list.add(ModuleRegistry.builder()
                .moduleKey(Modules.ATTENDANCE_CONFIG).displayName("Attendance Config").parent("HR").sortOrder(order++)
                .operations(new ArrayList<>(FULL)).build());

        // ---- REPORTS ----
        list.add(ModuleRegistry.builder()
                .moduleKey("REPORTS").displayName("Reports").parent("REPORTS").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view", "export", "print", "download"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("CLIENT_ENTRY_REPORT").displayName("Client Entry Report").parent("REPORTS").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view", "delete", "export", "print", "download"))).build());
        list.add(ModuleRegistry.builder()
                .moduleKey("SMALL_CLIENT_ENTRY_REPORT").displayName("Small Client Entry Report").parent("REPORTS").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view", "delete", "export", "print", "download"))).build());

        // ---- SETTINGS ----
        list.add(ModuleRegistry.builder()
                .moduleKey("SETTINGS").displayName("Company Settings").parent("SETTINGS").sortOrder(order++)
                .operations(new ArrayList<>(List.of("view", "edit"))).build());

        return list;
    }
}

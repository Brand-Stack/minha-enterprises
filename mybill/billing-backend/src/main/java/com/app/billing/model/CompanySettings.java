package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "company_settings")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanySettings extends BaseEntity {
    private String companyName;
    /** Shown in email signatures (e.g. Thanks and Regards). */
    private String ownerName;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    private String mobile; // Mobile number for invoices (e.g. Contact: 9445844756)
    private String email;
    private String gstin;

    /** HSN/SAC Code for invoices (e.g. 996812) - configurable, not hardcoded */
    private String hsnSacCode;

    /** LUT ARN number (shown on monthly courier invoice PDF below HSN/SAC). */
    private String lutArnNo;

    // Legacy single bank (kept for backward compatibility; prefer bankAccounts when present)
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private String bankIfscCode;

    /** Multiple bank accounts for RTGS/NEFT selection in quotations/invoices */
    @lombok.Builder.Default
    private List<BankAccount> bankAccounts = new ArrayList<>();
    private String footerSlogan;
    private String footerForQuotation; // Footer text specifically for Quotations
    private String printFormat; // A4, THERMAL, COMPACT
    private String logoPath; // Path to uploaded logo file
    private String logoBase64; // Base64 encoded logo for display
    
    // Invoice Number Configuration
    private String invoiceNumberMode; // AUTO or MANUAL
    private Integer lastSeriesNo; // Last generated sequence number
    private Integer year; // Current active financial year (starting year e.g. 2026)
    /** Starting sequence for FY invoice numbers (Client/Small Client Entry). Default 1 → 001/2026. */
    private Integer invoiceStartingSequence;
    
    // Configurable arrays for Shipment Breakup dropdowns
    @lombok.Builder.Default
    private List<String> couriers = new ArrayList<>();
    
    @lombok.Builder.Default
    private List<String> items = new ArrayList<>();
    
    @lombok.Builder.Default
    private List<String> statuses = new ArrayList<>();

    /** Step between bulk-generated AWB numbers (positive integer, default 1). */
    private Integer awbFrequency;

    /** Default GST % for client-entry when quotation GST % is blank. Null = 0%. */
    private Double defaultGstPercentage;

    /** Configurable user idle auto-logout time in minutes. Default 15 minutes. */
    private Integer autoLogoutMinutes;

    // Attendance & Working Hours Settings
    @lombok.Builder.Default
    private String workingStartTime = "10:00"; // HH:mm format
    @lombok.Builder.Default
    private String workingEndTime = "18:00"; // HH:mm format
    @lombok.Builder.Default
    private Double workingHoursPerDay = 8.0;
    @lombok.Builder.Default
    private Integer lateGracePeriodMinutes = 15;
    @lombok.Builder.Default
    private Integer earlyCheckoutGracePeriodMinutes = 15;
    @lombok.Builder.Default
    private Integer breakDurationMinutes = 45;
    @lombok.Builder.Default
    private Integer overtimeThresholdMinutes = 30;
    @lombok.Builder.Default
    private List<String> workingDays = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY");
    @lombok.Builder.Default
    private List<String> weekendDays = List.of("SUNDAY");
    @lombok.Builder.Default
    private Integer casualLeaveEntitlementPerYear = 12;
    @lombok.Builder.Default
    private Integer medicalLeaveEntitlementPerYear = 10;
    @lombok.Builder.Default
    private Integer emergencyLeaveEntitlementPerYear = 5;
    @lombok.Builder.Default
    private Integer compOffEntitlementPerYear = 3;
    @lombok.Builder.Default
    private Double overtimeHourlyRateMultiplier = 1.5;
    @lombok.Builder.Default
    private String lopDailyCalculationMethod = "CALENDAR_DAYS"; // CALENDAR_DAYS or WORKING_DAYS

    // Geofencing Settings
    private String attendanceLocation;
    private Double officeLatitude;
    private Double officeLongitude;
    @lombok.Builder.Default
    private Double allowedRadiusMeters = 100.0;
    @lombok.Builder.Default
    private Boolean geofencingEnabled = false;
}


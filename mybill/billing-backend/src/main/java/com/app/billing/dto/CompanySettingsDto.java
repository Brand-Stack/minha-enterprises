package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySettingsDto {
    private String id;
    private String companyName;
    private String ownerName;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    private String mobile;
    private String email;
    private String gstin;
    private String hsnSacCode;
    private String lutArnNo;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private String bankIfscCode;
    @Builder.Default
    private List<BankAccountDto> bankAccounts = new ArrayList<>();
    private String footerSlogan;
    private String footerForQuotation; // Footer text specifically for Quotations
    private String printFormat; // A4, THERMAL, COMPACT
    private String logoPath;
    private String logoBase64;
    
    // Invoice Number Configuration
    private String invoiceNumberMode; // AUTO or MANUAL
    private Integer lastSeriesNo; // Last generated sequence number
    private Integer year; // Current active financial year (starting year e.g. 2026)
    /** Starting sequence for FY invoice numbers (Client/Small Client Entry). Default 1 → 001/2026. */
    private Integer invoiceStartingSequence;
    
    @Builder.Default
    private List<String> couriers = new ArrayList<>();
    
    @Builder.Default
    private List<String> items = new ArrayList<>();
    
    @Builder.Default
    private List<String> statuses = new ArrayList<>();

    /** Bulk AWB generation step (positive integer, default 1). */
    private Integer awbFrequency;

    /** Default GST % when client-entry GST % is blank. Null = 0%. */
    private Double defaultGstPercentage;

    /** Configurable user idle auto-logout time in minutes. Default 15 minutes. */
    private Integer autoLogoutMinutes;

    // Attendance & Working Hours Settings
    @Builder.Default
    private String workingStartTime = "10:00";
    @Builder.Default
    private String workingEndTime = "18:00";
    @Builder.Default
    private Double workingHoursPerDay = 8.0;
    @Builder.Default
    private Integer lateGracePeriodMinutes = 15;
    @Builder.Default
    private Integer earlyCheckoutGracePeriodMinutes = 15;
    @Builder.Default
    private Integer breakDurationMinutes = 45;
    @Builder.Default
    private Integer overtimeThresholdMinutes = 30;
    @Builder.Default
    private List<String> workingDays = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY");
    @Builder.Default
    private List<String> weekendDays = List.of("SUNDAY");
    @Builder.Default
    private Integer casualLeaveEntitlementPerYear = 12;
    @Builder.Default
    private Integer medicalLeaveEntitlementPerYear = 10;
    @Builder.Default
    private Integer emergencyLeaveEntitlementPerYear = 5;
    @Builder.Default
    private Integer compOffEntitlementPerYear = 3;
    @Builder.Default
    private Double overtimeHourlyRateMultiplier = 1.5;
    @Builder.Default
    private String lopDailyCalculationMethod = "CALENDAR_DAYS";

    // Geofencing Settings
    private String attendanceLocation;
    private Double officeLatitude;
    private Double officeLongitude;
    @Builder.Default
    private Double allowedRadiusMeters = 100.0;
    @Builder.Default
    private Boolean geofencingEnabled = false;
}


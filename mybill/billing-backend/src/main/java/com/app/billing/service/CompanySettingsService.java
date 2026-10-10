package com.app.billing.service;

import com.app.billing.dao.CompanySettingsRepository;
import com.app.billing.dto.BankAccountDto;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.BankAccount;
import com.app.billing.model.CompanySettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {
    
    private final CompanySettingsRepository companySettingsRepository;
    
    public CompanySettingsDto getSettings() {
        Optional<CompanySettings> settings = companySettingsRepository.findFirstByOrderByCreatedAtDesc();
        if (settings.isPresent()) {
            return toDto(settings.get());
        }
        // Return default settings if none exist
        return CompanySettingsDto.builder()
                .companyName("")
                .ownerName("")
                .address("")
                .city("")
                .state("")
                .pincode("")
                .phone("")
                .mobile("")
                .email("")
                .gstin("")
                .hsnSacCode("")
                .lutArnNo("")
                .bankAccountName("")
                .bankAccountNumber("")
                .bankName("")
                .bankBranch("")
                .bankIfscCode("")
                .bankAccounts(new java.util.ArrayList<>())
                .footerSlogan("Thank you for your business!")
                .footerForQuotation("")
                .printFormat("A4")
                .awbFrequency(1)
                .defaultGstPercentage(null)
                .autoLogoutMinutes(15)
                .attendanceLocation("Company Office")
                .officeLatitude(null)
                .officeLongitude(null)
                .allowedRadiusMeters(100.0)
                .geofencingEnabled(false)
                .build();
    }
    
    public CompanySettingsDto saveSettings(CompanySettingsDto dto) {
        Optional<CompanySettings> existing = companySettingsRepository.findFirstByOrderByCreatedAtDesc();
        CompanySettings settings;
        
        if (existing.isPresent()) {
            settings = existing.get();
            if (settings != null) {
                updateEntity(settings, dto);
            } else {
                settings = toEntity(dto);
            }
        } else {
            settings = toEntity(dto);
        }
        
        CompanySettings saved = companySettingsRepository.save(settings);
        return toDto(saved);
    }
    
    private CompanySettings toEntity(CompanySettingsDto dto) {
        return CompanySettings.builder()
                .companyName(dto.getCompanyName())
                .ownerName(dto.getOwnerName())
                .address(dto.getAddress())
                .city(dto.getCity())
                .state(dto.getState())
                .pincode(dto.getPincode())
                .phone(dto.getPhone())
                .mobile(dto.getMobile())
                .email(dto.getEmail())
                .gstin(dto.getGstin())
                .hsnSacCode(dto.getHsnSacCode())
                .lutArnNo(dto.getLutArnNo())
                .bankAccountName(dto.getBankAccountName())
                .bankAccountNumber(dto.getBankAccountNumber())
                .bankName(dto.getBankName())
                .bankBranch(dto.getBankBranch())
                .bankIfscCode(dto.getBankIfscCode())
                .footerSlogan(dto.getFooterSlogan())
                .footerForQuotation(dto.getFooterForQuotation())
                .printFormat(dto.getPrintFormat() != null ? dto.getPrintFormat() : "A4")
                .logoPath(dto.getLogoPath())
                .logoBase64(dto.getLogoBase64())
                .invoiceNumberMode(dto.getInvoiceNumberMode() != null ? dto.getInvoiceNumberMode() : "AUTO")
                .lastSeriesNo(dto.getLastSeriesNo())
                .year(dto.getYear())
                .invoiceStartingSequence(dto.getInvoiceStartingSequence() != null && dto.getInvoiceStartingSequence() > 0
                        ? dto.getInvoiceStartingSequence() : 1)
                .bankAccounts(mapBankAccountsToEntity(dto.getBankAccounts()))
                .couriers(dto.getCouriers() != null ? dto.getCouriers() : new ArrayList<>())
                .items(dto.getItems() != null ? dto.getItems() : new ArrayList<>())
                .statuses(dto.getStatuses() != null ? dto.getStatuses() : new ArrayList<>())
                .awbFrequency(dto.getAwbFrequency() != null && dto.getAwbFrequency() > 0 ? dto.getAwbFrequency() : 1)
                .defaultGstPercentage(validateDefaultGst(dto.getDefaultGstPercentage()))
                .autoLogoutMinutes(validateAutoLogout(dto.getAutoLogoutMinutes()))
                .workingStartTime(dto.getWorkingStartTime() != null ? dto.getWorkingStartTime() : "10:00")
                .workingEndTime(dto.getWorkingEndTime() != null ? dto.getWorkingEndTime() : "18:00")
                .workingHoursPerDay(dto.getWorkingHoursPerDay() != null ? dto.getWorkingHoursPerDay() : 8.0)
                .lateGracePeriodMinutes(dto.getLateGracePeriodMinutes() != null ? dto.getLateGracePeriodMinutes() : 15)
                .earlyCheckoutGracePeriodMinutes(dto.getEarlyCheckoutGracePeriodMinutes() != null ? dto.getEarlyCheckoutGracePeriodMinutes() : 15)
                .breakDurationMinutes(dto.getBreakDurationMinutes() != null ? dto.getBreakDurationMinutes() : 45)
                .overtimeThresholdMinutes(dto.getOvertimeThresholdMinutes() != null ? dto.getOvertimeThresholdMinutes() : 30)
                .workingDays(dto.getWorkingDays() != null ? dto.getWorkingDays() : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"))
                .weekendDays(dto.getWeekendDays() != null ? dto.getWeekendDays() : List.of("SUNDAY"))
                .casualLeaveEntitlementPerYear(dto.getCasualLeaveEntitlementPerYear() != null ? dto.getCasualLeaveEntitlementPerYear() : 12)
                .medicalLeaveEntitlementPerYear(dto.getMedicalLeaveEntitlementPerYear() != null ? dto.getMedicalLeaveEntitlementPerYear() : 10)
                .emergencyLeaveEntitlementPerYear(dto.getEmergencyLeaveEntitlementPerYear() != null ? dto.getEmergencyLeaveEntitlementPerYear() : 5)
                .compOffEntitlementPerYear(dto.getCompOffEntitlementPerYear() != null ? dto.getCompOffEntitlementPerYear() : 3)
                .overtimeHourlyRateMultiplier(dto.getOvertimeHourlyRateMultiplier() != null ? dto.getOvertimeHourlyRateMultiplier() : 1.5)
                .lopDailyCalculationMethod(dto.getLopDailyCalculationMethod() != null ? dto.getLopDailyCalculationMethod() : "CALENDAR_DAYS")
                .attendanceLocation(dto.getAttendanceLocation())
                .officeLatitude(dto.getOfficeLatitude())
                .officeLongitude(dto.getOfficeLongitude())
                .allowedRadiusMeters(dto.getAllowedRadiusMeters() != null ? dto.getAllowedRadiusMeters() : 100.0)
                .geofencingEnabled(dto.getGeofencingEnabled() != null ? dto.getGeofencingEnabled() : false)
                .build();
    }
    
    private void updateEntity(CompanySettings entity, CompanySettingsDto dto) {
        if (dto.getCompanyName() != null) entity.setCompanyName(dto.getCompanyName());
        if (dto.getOwnerName() != null) entity.setOwnerName(dto.getOwnerName());
        if (dto.getAddress() != null) entity.setAddress(dto.getAddress());
        if (dto.getCity() != null) entity.setCity(dto.getCity());
        if (dto.getState() != null) entity.setState(dto.getState());
        if (dto.getPincode() != null) entity.setPincode(dto.getPincode());
        if (dto.getPhone() != null) entity.setPhone(dto.getPhone());
        if (dto.getMobile() != null) entity.setMobile(dto.getMobile());
        if (dto.getEmail() != null) entity.setEmail(dto.getEmail());
        if (dto.getGstin() != null) entity.setGstin(dto.getGstin());
        if (dto.getHsnSacCode() != null) entity.setHsnSacCode(dto.getHsnSacCode());
        if (dto.getLutArnNo() != null) entity.setLutArnNo(dto.getLutArnNo());
        if (dto.getBankAccountName() != null) entity.setBankAccountName(dto.getBankAccountName());
        if (dto.getBankAccountNumber() != null) entity.setBankAccountNumber(dto.getBankAccountNumber());
        if (dto.getBankName() != null) entity.setBankName(dto.getBankName());
        if (dto.getBankBranch() != null) entity.setBankBranch(dto.getBankBranch());
        if (dto.getBankIfscCode() != null) entity.setBankIfscCode(dto.getBankIfscCode());
        if (dto.getBankAccounts() != null) entity.setBankAccounts(mapBankAccountsToEntity(dto.getBankAccounts()));
        if (dto.getFooterSlogan() != null) entity.setFooterSlogan(dto.getFooterSlogan());
        if (dto.getFooterForQuotation() != null) entity.setFooterForQuotation(dto.getFooterForQuotation());
        if (dto.getPrintFormat() != null) entity.setPrintFormat(dto.getPrintFormat());
        if (dto.getLogoPath() != null) entity.setLogoPath(dto.getLogoPath());
        if (dto.getLogoBase64() != null) entity.setLogoBase64(dto.getLogoBase64());
        if (dto.getInvoiceNumberMode() != null) entity.setInvoiceNumberMode(dto.getInvoiceNumberMode());
        entity.setLastSeriesNo(dto.getLastSeriesNo());
        entity.setYear(dto.getYear());
        if (dto.getInvoiceStartingSequence() != null) {
            entity.setInvoiceStartingSequence(dto.getInvoiceStartingSequence() > 0 ? dto.getInvoiceStartingSequence() : 1);
        }
        if (dto.getCouriers() != null) entity.setCouriers(dto.getCouriers());
        if (dto.getItems() != null) entity.setItems(dto.getItems());
        if (dto.getStatuses() != null) entity.setStatuses(dto.getStatuses());
        if (dto.getAwbFrequency() != null) {
            entity.setAwbFrequency(dto.getAwbFrequency() > 0 ? dto.getAwbFrequency() : 1);
        }
        if (dto.getDefaultGstPercentage() != null) {
            entity.setDefaultGstPercentage(validateDefaultGst(dto.getDefaultGstPercentage()));
        }
        if (dto.getAutoLogoutMinutes() != null) {
            entity.setAutoLogoutMinutes(validateAutoLogout(dto.getAutoLogoutMinutes()));
        }
        if (dto.getWorkingStartTime() != null) entity.setWorkingStartTime(dto.getWorkingStartTime());
        if (dto.getWorkingEndTime() != null) entity.setWorkingEndTime(dto.getWorkingEndTime());
        if (dto.getWorkingHoursPerDay() != null) entity.setWorkingHoursPerDay(dto.getWorkingHoursPerDay());
        if (dto.getLateGracePeriodMinutes() != null) entity.setLateGracePeriodMinutes(dto.getLateGracePeriodMinutes());
        if (dto.getEarlyCheckoutGracePeriodMinutes() != null) entity.setEarlyCheckoutGracePeriodMinutes(dto.getEarlyCheckoutGracePeriodMinutes());
        if (dto.getBreakDurationMinutes() != null) entity.setBreakDurationMinutes(dto.getBreakDurationMinutes());
        if (dto.getOvertimeThresholdMinutes() != null) entity.setOvertimeThresholdMinutes(dto.getOvertimeThresholdMinutes());
        if (dto.getWorkingDays() != null) entity.setWorkingDays(dto.getWorkingDays());
        if (dto.getWeekendDays() != null) entity.setWeekendDays(dto.getWeekendDays());
        if (dto.getCasualLeaveEntitlementPerYear() != null) entity.setCasualLeaveEntitlementPerYear(dto.getCasualLeaveEntitlementPerYear());
        if (dto.getMedicalLeaveEntitlementPerYear() != null) entity.setMedicalLeaveEntitlementPerYear(dto.getMedicalLeaveEntitlementPerYear());
        if (dto.getEmergencyLeaveEntitlementPerYear() != null) entity.setEmergencyLeaveEntitlementPerYear(dto.getEmergencyLeaveEntitlementPerYear());
        if (dto.getCompOffEntitlementPerYear() != null) entity.setCompOffEntitlementPerYear(dto.getCompOffEntitlementPerYear());
        if (dto.getOvertimeHourlyRateMultiplier() != null) entity.setOvertimeHourlyRateMultiplier(dto.getOvertimeHourlyRateMultiplier());
        if (dto.getLopDailyCalculationMethod() != null) entity.setLopDailyCalculationMethod(dto.getLopDailyCalculationMethod());
        if (dto.getAttendanceLocation() != null) entity.setAttendanceLocation(dto.getAttendanceLocation());
        entity.setOfficeLatitude(dto.getOfficeLatitude());
        entity.setOfficeLongitude(dto.getOfficeLongitude());
        if (dto.getAllowedRadiusMeters() != null) entity.setAllowedRadiusMeters(dto.getAllowedRadiusMeters());
        if (dto.getGeofencingEnabled() != null) entity.setGeofencingEnabled(dto.getGeofencingEnabled());
    }

    private static Integer validateAutoLogout(Integer minutes) {
        if (minutes == null) {
            return 15;
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("Auto logout time must be a positive number of minutes (at least 1)");
        }
        return minutes;
    }

    private static Double validateDefaultGst(Double value) {
        if (value == null || value.isNaN()) {
            return null;
        }
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Default GST percentage must be between 0 and 100");
        }
        return value;
    }
    
    private CompanySettingsDto toDto(CompanySettings entity) {
        return CompanySettingsDto.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .ownerName(entity.getOwnerName())
                .address(entity.getAddress())
                .city(entity.getCity())
                .state(entity.getState())
                .pincode(entity.getPincode())
                .phone(entity.getPhone())
                .mobile(entity.getMobile())
                .email(entity.getEmail())
                .gstin(entity.getGstin())
                .hsnSacCode(entity.getHsnSacCode())
                .lutArnNo(entity.getLutArnNo())
                .bankAccountName(entity.getBankAccountName())
                .bankAccountNumber(entity.getBankAccountNumber())
                .bankName(entity.getBankName())
                .bankBranch(entity.getBankBranch())
                .bankIfscCode(entity.getBankIfscCode())
                .footerSlogan(entity.getFooterSlogan())
                .footerForQuotation(entity.getFooterForQuotation())
                .printFormat(entity.getPrintFormat())
                .logoPath(entity.getLogoPath())
                .logoBase64(entity.getLogoBase64())
                .invoiceNumberMode(entity.getInvoiceNumberMode())
                .lastSeriesNo(entity.getLastSeriesNo())
                .year(entity.getYear())
                .invoiceStartingSequence(entity.getInvoiceStartingSequence() != null && entity.getInvoiceStartingSequence() > 0
                        ? entity.getInvoiceStartingSequence() : 1)
                .bankAccounts(mapBankAccountsToDto(entity.getBankAccounts()))
                .couriers(entity.getCouriers() != null ? entity.getCouriers() : new ArrayList<>())
                .items(entity.getItems() != null ? entity.getItems() : new ArrayList<>())
                .statuses(entity.getStatuses() != null ? entity.getStatuses() : new ArrayList<>())
                .awbFrequency(entity.getAwbFrequency() != null && entity.getAwbFrequency() > 0 ? entity.getAwbFrequency() : 1)
                .defaultGstPercentage(entity.getDefaultGstPercentage())
                .autoLogoutMinutes(entity.getAutoLogoutMinutes() != null && entity.getAutoLogoutMinutes() > 0 ? entity.getAutoLogoutMinutes() : 15)
                .workingStartTime(entity.getWorkingStartTime() != null ? entity.getWorkingStartTime() : "10:00")
                .workingEndTime(entity.getWorkingEndTime() != null ? entity.getWorkingEndTime() : "18:00")
                .workingHoursPerDay(entity.getWorkingHoursPerDay() != null ? entity.getWorkingHoursPerDay() : 8.0)
                .lateGracePeriodMinutes(entity.getLateGracePeriodMinutes() != null ? entity.getLateGracePeriodMinutes() : 15)
                .earlyCheckoutGracePeriodMinutes(entity.getEarlyCheckoutGracePeriodMinutes() != null ? entity.getEarlyCheckoutGracePeriodMinutes() : 15)
                .breakDurationMinutes(entity.getBreakDurationMinutes() != null ? entity.getBreakDurationMinutes() : 45)
                .overtimeThresholdMinutes(entity.getOvertimeThresholdMinutes() != null ? entity.getOvertimeThresholdMinutes() : 30)
                .workingDays(entity.getWorkingDays() != null ? entity.getWorkingDays() : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"))
                .weekendDays(entity.getWeekendDays() != null ? entity.getWeekendDays() : List.of("SUNDAY"))
                .casualLeaveEntitlementPerYear(entity.getCasualLeaveEntitlementPerYear() != null ? entity.getCasualLeaveEntitlementPerYear() : 12)
                .medicalLeaveEntitlementPerYear(entity.getMedicalLeaveEntitlementPerYear() != null ? entity.getMedicalLeaveEntitlementPerYear() : 10)
                .emergencyLeaveEntitlementPerYear(entity.getEmergencyLeaveEntitlementPerYear() != null ? entity.getEmergencyLeaveEntitlementPerYear() : 5)
                .compOffEntitlementPerYear(entity.getCompOffEntitlementPerYear() != null ? entity.getCompOffEntitlementPerYear() : 3)
                .overtimeHourlyRateMultiplier(entity.getOvertimeHourlyRateMultiplier() != null ? entity.getOvertimeHourlyRateMultiplier() : 1.5)
                .lopDailyCalculationMethod(entity.getLopDailyCalculationMethod() != null ? entity.getLopDailyCalculationMethod() : "CALENDAR_DAYS")
                .attendanceLocation(entity.getAttendanceLocation())
                .officeLatitude(entity.getOfficeLatitude())
                .officeLongitude(entity.getOfficeLongitude())
                .allowedRadiusMeters(entity.getAllowedRadiusMeters() != null ? entity.getAllowedRadiusMeters() : 100.0)
                .geofencingEnabled(entity.getGeofencingEnabled() != null ? entity.getGeofencingEnabled() : false)
                .build();
    }

    private List<BankAccount> mapBankAccountsToEntity(List<BankAccountDto> list) {
        if (list == null) return new ArrayList<>();
        return list.stream().map(d -> BankAccount.builder()
                .id(d.getId())
                .accountName(d.getAccountName())
                .accountNumber(d.getAccountNumber())
                .bankName(d.getBankName())
                .branch(d.getBranch())
                .ifscCode(d.getIfscCode())
                .build()).collect(Collectors.toList());
    }

    private List<BankAccountDto> mapBankAccountsToDto(List<BankAccount> list) {
        if (list == null) return new ArrayList<>();
        return list.stream().map(e -> BankAccountDto.builder()
                .id(e.getId())
                .accountName(e.getAccountName())
                .accountNumber(e.getAccountNumber())
                .bankName(e.getBankName())
                .branch(e.getBranch())
                .ifscCode(e.getIfscCode())
                .build()).collect(Collectors.toList());
    }
}


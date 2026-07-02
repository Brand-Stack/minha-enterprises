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
                .invoicePrefix(dto.getInvoicePrefix() != null ? dto.getInvoicePrefix() : "INV-")
                .estimatePrefix(dto.getEstimatePrefix() != null ? dto.getEstimatePrefix() : "EST-")
                .invoiceStartingSequence(dto.getInvoiceStartingSequence() != null && dto.getInvoiceStartingSequence() > 0
                        ? dto.getInvoiceStartingSequence() : 1)
                .bankAccounts(mapBankAccountsToEntity(dto.getBankAccounts()))
                .couriers(dto.getCouriers() != null ? dto.getCouriers() : new ArrayList<>())
                .items(dto.getItems() != null ? dto.getItems() : new ArrayList<>())
                .statuses(dto.getStatuses() != null ? dto.getStatuses() : new ArrayList<>())
                .awbFrequency(dto.getAwbFrequency() != null && dto.getAwbFrequency() > 0 ? dto.getAwbFrequency() : 1)
                .defaultGstPercentage(validateDefaultGst(dto.getDefaultGstPercentage()))
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
        if (dto.getInvoicePrefix() != null) entity.setInvoicePrefix(dto.getInvoicePrefix());
        if (dto.getEstimatePrefix() != null) entity.setEstimatePrefix(dto.getEstimatePrefix());
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
                .invoicePrefix(entity.getInvoicePrefix())
                .estimatePrefix(entity.getEstimatePrefix())
                .invoiceStartingSequence(entity.getInvoiceStartingSequence() != null && entity.getInvoiceStartingSequence() > 0
                        ? entity.getInvoiceStartingSequence() : 1)
                .bankAccounts(mapBankAccountsToDto(entity.getBankAccounts()))
                .couriers(entity.getCouriers() != null ? entity.getCouriers() : new ArrayList<>())
                .items(entity.getItems() != null ? entity.getItems() : new ArrayList<>())
                .statuses(entity.getStatuses() != null ? entity.getStatuses() : new ArrayList<>())
                .awbFrequency(entity.getAwbFrequency() != null && entity.getAwbFrequency() > 0 ? entity.getAwbFrequency() : 1)
                .defaultGstPercentage(entity.getDefaultGstPercentage())
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


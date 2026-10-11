package com.app.billing.service;

import com.app.billing.dao.OnboardQuotationRepository;
import com.app.billing.dto.OnboardQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.dto.ZoneRateConfigDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CourierQuotation.QuotationStatus;
import com.app.billing.model.OnboardQuotation;
import com.app.billing.model.OnboardQuotation.OnboardSlab;
import com.app.billing.model.ZoneRateConfig;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardQuotationService {

    private final OnboardQuotationRepository repository;
    private final AuditUtil auditUtil;

    @Transactional
    public OnboardQuotationDto create(OnboardQuotationDto dto) {
        validateDates(dto);

        OnboardQuotation entity = toEntity(dto);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : QuotationStatus.DRAFT);

        if (StringUtils.hasText(dto.getQuotationNumber())) {
            entity.setQuotationNumber(dto.getQuotationNumber().trim());
        } else {
            entity.setQuotationNumber(generateNextNumber());
        }

        ensureSlabIds(entity);
        auditUtil.setCreatedBy(entity);

        return toDto(repository.save(entity));
    }

    @Transactional
    public OnboardQuotationDto update(String id, OnboardQuotationDto dto) {
        OnboardQuotation existing = findEntityById(id);
        validateDates(dto);

        existing.setCustomerName(dto.getCustomerName());
        existing.setBranchName(dto.getBranchName());
        existing.setEffectiveDate(dto.getEffectiveDate());
        existing.setValidTillDate(dto.getValidTillDate());
        existing.setFuelChargePercentage(dto.getFuelChargePercentage());
        existing.setFovCharges(dto.getFovCharges());
        existing.setSelectedBankAccountId(dto.getSelectedBankAccountId());
        existing.setRemarks(dto.getRemarks());
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }

        if (dto.getSlabs() != null) {
            existing.setSlabs(dto.getSlabs().stream().map(this::toSlabEntity).collect(Collectors.toList()));
        } else {
            existing.setSlabs(new ArrayList<>());
        }

        ensureSlabIds(existing);
        auditUtil.setUpdatedBy(existing);

        return toDto(repository.save(existing));
    }

    @Transactional(readOnly = true)
    public OnboardQuotationDto findById(String id) {
        return toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OnboardQuotationDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<OnboardQuotation> p = repository.findAll(pageable);
        List<OnboardQuotationDto> content = p.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(content, p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PageResponse<OnboardQuotationDto> search(String searchTerm, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        String term = StringUtils.hasText(searchTerm) ? searchTerm.trim() : "";
        Page<OnboardQuotation> p = repository.findByCustomerNameContainingIgnoreCaseOrQuotationNumberContainingIgnoreCase(term, term, pageable);
        List<OnboardQuotationDto> content = p.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(content, p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PageResponse<OnboardQuotationDto> findWithFiltersAndSearch(LocalDate effectiveFrom, LocalDate effectiveTo, String statusStr, String searchTerm, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        QuotationStatus status = null;
        if (StringUtils.hasText(statusStr)) {
            try {
                status = QuotationStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        String regex = StringUtils.hasText(searchTerm) ? searchTerm.trim() : "";
        Page<OnboardQuotation> p = repository.findWithFiltersAndSearch(effectiveFrom, effectiveTo, status, regex, pageable);
        List<OnboardQuotationDto> content = p.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(content, p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Transactional
    public void delete(String id) {
        OnboardQuotation existing = findEntityById(id);
        repository.delete(existing);
    }

    private OnboardQuotation findEntityById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Onboard quotation not found: " + id));
    }

    private void validateDates(OnboardQuotationDto dto) {
        if (dto.getEffectiveDate() != null && dto.getValidTillDate() != null) {
            if (dto.getValidTillDate().isBefore(dto.getEffectiveDate())) {
                throw new IllegalArgumentException("Valid till date cannot be before effective date");
            }
        }
    }

    private void ensureSlabIds(OnboardQuotation entity) {
        if (entity.getSlabs() != null) {
            for (OnboardSlab slab : entity.getSlabs()) {
                if (!StringUtils.hasText(slab.getSlabId())) {
                    slab.setSlabId(UUID.randomUUID().toString());
                }
            }
        }
    }

    private synchronized String generateNextNumber() {
        int year = LocalDate.now().getYear();
        long count = repository.count() + 1;
        return String.format("ONB-%d-%04d", year, count);
    }

    private OnboardQuotation toEntity(OnboardQuotationDto dto) {
        return OnboardQuotation.builder()
                .id(dto.getId())
                .quotationNumber(dto.getQuotationNumber())
                .customerName(dto.getCustomerName())
                .branchName(dto.getBranchName())
                .effectiveDate(dto.getEffectiveDate())
                .validTillDate(dto.getValidTillDate())
                .remarks(dto.getRemarks())
                .status(dto.getStatus())
                .fuelChargePercentage(dto.getFuelChargePercentage())
                .fovCharges(dto.getFovCharges())
                .selectedBankAccountId(dto.getSelectedBankAccountId())
                .slabs(dto.getSlabs() != null ? dto.getSlabs().stream().map(this::toSlabEntity).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private OnboardSlab toSlabEntity(OnboardQuotationDto.OnboardSlabDto dto) {
        return OnboardSlab.builder()
                .slabId(StringUtils.hasText(dto.getSlabId()) ? dto.getSlabId() : UUID.randomUUID().toString())
                .slabName(dto.getSlabName())
                .selected(dto.getSelected() != null ? dto.getSelected() : true)
                .zoneRates(dto.getZoneRates() != null ? dto.getZoneRates().stream().map(this::toZoneRateEntity).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private ZoneRateConfig toZoneRateEntity(ZoneRateConfigDto dto) {
        return ZoneRateConfig.builder()
                .zoneId(dto.getZoneId())
                .zoneName(dto.getZoneName())
                .expressBaseRate(dto.getExpressBaseRate())
                .expressIncrementalRate(dto.getExpressIncrementalRate())
                .expressPerKgRate(dto.getExpressPerKgRate())
                .surfaceSlab1Rate(dto.getSurfaceSlab1Rate())
                .surfaceSlab2Rate(dto.getSurfaceSlab2Rate())
                .standardBaseRate3Kg(dto.getStandardBaseRate3Kg())
                .standardAdditionalPerKg(dto.getStandardAdditionalPerKg())
                .standardRate1Kg(dto.getStandardRate1Kg())
                .standardRate2Kg(dto.getStandardRate2Kg())
                .standardRate3Kg(dto.getStandardRate3Kg())
                .standardPerKgAbove3(dto.getStandardPerKgAbove3())
                .standardRate4Kg(dto.getStandardRate4Kg())
                .standardRate5Kg(dto.getStandardRate5Kg())
                .build();
    }

    public OnboardQuotationDto toDto(OnboardQuotation entity) {
        return OnboardQuotationDto.builder()
                .id(entity.getId())
                .quotationNumber(entity.getQuotationNumber())
                .customerName(entity.getCustomerName())
                .branchName(entity.getBranchName())
                .effectiveDate(entity.getEffectiveDate())
                .validTillDate(entity.getValidTillDate())
                .remarks(entity.getRemarks())
                .status(entity.getStatus())
                .fuelChargePercentage(entity.getFuelChargePercentage())
                .fovCharges(entity.getFovCharges())
                .selectedBankAccountId(entity.getSelectedBankAccountId())
                .slabs(entity.getSlabs() != null ? entity.getSlabs().stream().map(this::toSlabDto).collect(Collectors.toList()) : new ArrayList<>())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    private OnboardQuotationDto.OnboardSlabDto toSlabDto(OnboardSlab entity) {
        return OnboardQuotationDto.OnboardSlabDto.builder()
                .slabId(entity.getSlabId())
                .slabName(entity.getSlabName())
                .selected(entity.getSelected() != null ? entity.getSelected() : true)
                .zoneRates(entity.getZoneRates() != null ? entity.getZoneRates().stream().map(this::toZoneRateDto).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private ZoneRateConfigDto toZoneRateDto(ZoneRateConfig entity) {
        return ZoneRateConfigDto.builder()
                .zoneId(entity.getZoneId())
                .zoneName(entity.getZoneName())
                .expressBaseRate(entity.getExpressBaseRate())
                .expressIncrementalRate(entity.getExpressIncrementalRate())
                .expressPerKgRate(entity.getExpressPerKgRate())
                .surfaceSlab1Rate(entity.getSurfaceSlab1Rate())
                .surfaceSlab2Rate(entity.getSurfaceSlab2Rate())
                .standardBaseRate3Kg(entity.getStandardBaseRate3Kg())
                .standardAdditionalPerKg(entity.getStandardAdditionalPerKg())
                .standardRate1Kg(entity.getStandardRate1Kg())
                .standardRate2Kg(entity.getStandardRate2Kg())
                .standardRate3Kg(entity.getStandardRate3Kg())
                .standardPerKgAbove3(entity.getStandardPerKgAbove3())
                .standardRate4Kg(entity.getStandardRate4Kg())
                .standardRate5Kg(entity.getStandardRate5Kg())
                .build();
    }
}

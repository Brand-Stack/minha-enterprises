package com.app.billing.service;

import com.app.billing.dao.CourierQuotationRepository;
import com.app.billing.dao.ClientRepository;
import com.app.billing.dto.CourierQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.Client;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierQuotationService {

    private final CourierQuotationRepository repository;
    private final ClientRepository clientRepository;
    private final AuditUtil auditUtil;

    @Transactional
    public CourierQuotationDto create(CourierQuotationDto dto) {
        validateDates(dto);
        Client client = clientRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + dto.getCustomerId()));

        CourierQuotation entity = toEntity(dto);
        entity.setCustomerName(client.getPartyName());
        entity.setStatus(CourierQuotation.QuotationStatus.DRAFT);

        if (dto.getQuotationNumber() != null && !dto.getQuotationNumber().trim().isEmpty()) {
            String manual = dto.getQuotationNumber().trim();
            if (repository.existsByQuotationNumber(manual)) {
                throw new ResourceAlreadyExistsException("Quotation number already exists: " + manual);
            }
            entity.setQuotationNumber(manual);
        } else {
            entity.setQuotationNumber(generateNextNumber());
        }

        auditUtil.setCreatedBy(entity);

        return toDto(repository.save(entity));
    }

    @Transactional
    public CourierQuotationDto update(String id, CourierQuotationDto dto) {
        CourierQuotation existing = findEntityById(id);
        validateDates(dto);

        Client client = clientRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + dto.getCustomerId()));

        updateEntity(existing, dto);
        existing.setCustomerName(client.getPartyName());
        auditUtil.setUpdatedBy(existing);

        return toDto(repository.save(existing));
    }

    public CourierQuotationDto findById(String id) {
        return toDto(findEntityById(id));
    }

    public PageResponse<CourierQuotationDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<CourierQuotation> result = repository.findAll(pageable);
        return PaginationUtil.toPageResponse(result.map(this::toDto));
    }

    public PageResponse<CourierQuotationDto> findWithFilters(LocalDate effectiveFrom, LocalDate effectiveTo,
            String status, int page, int size, String sortBy, String sortDir) {
        return findWithFiltersAndSearch(effectiveFrom, effectiveTo, status, null, page, size, sortBy, sortDir);
    }

    /**
     * Optional text search (quotation no, customer, branch) combined with date/status filters.
     */
    public PageResponse<CourierQuotationDto> findWithFiltersAndSearch(LocalDate effectiveFrom, LocalDate effectiveTo,
            String status, String searchTerm, int page, int size, String sortBy, String sortDir) {
        String statusParam = (status == null || status.trim().isEmpty()) ? "null" : status.trim();
        List<CourierQuotation> list = new ArrayList<>(
                repository.findWithFilters(effectiveFrom, effectiveTo, statusParam));
        if (StringUtils.hasText(searchTerm)) {
            String t = searchTerm.trim().toLowerCase(Locale.ROOT);
            list = list.stream()
                    .filter(q -> containsCi(q.getQuotationNumber(), t) || containsCi(q.getCustomerName(), t)
                            || containsCi(q.getBranchName(), t))
                    .collect(Collectors.toList());
        }
        Comparator<CourierQuotation> cmp = courierQuotationComparator(sortBy, sortDir);
        list.sort(cmp);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<CourierQuotation> paged = start < list.size() ? list.subList(start, end) : Collections.emptyList();
        return PaginationUtil.toPageResponse(
                paged.stream().map(this::toDto).collect(Collectors.toList()),
                page, size, list.size());
    }

    private static boolean containsCi(String field, String needleLower) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private static Comparator<CourierQuotation> courierQuotationComparator(String sortBy, String sortDir) {
        String key = sortBy != null ? sortBy : "effectiveDate";
        boolean desc = sortDir == null || "desc".equalsIgnoreCase(sortDir);
        Comparator<CourierQuotation> base;
        base = switch (key) {
            case "quotationNumber" -> Comparator.comparing(CourierQuotation::getQuotationNumber,
                    Comparator.nullsLast(String::compareToIgnoreCase));
            case "customerName" -> Comparator.comparing(CourierQuotation::getCustomerName,
                    Comparator.nullsLast(String::compareToIgnoreCase));
            case "createdAt" -> Comparator.comparing(CourierQuotation::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "validTillDate" -> Comparator.comparing(CourierQuotation::getValidTillDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(CourierQuotation::getEffectiveDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return desc ? base.reversed() : base;
    }

    public PageResponse<CourierQuotationDto> search(String term, int page, int size, String sortBy, String sortDir) {
        List<CourierQuotation> all = repository.searchCourierQuotations(term);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<CourierQuotation> paged = (start < all.size()) ? all.subList(start, end) : Collections.emptyList();
        return PaginationUtil.toPageResponse(
                paged.stream().map(this::toDto).collect(Collectors.toList()),
                page, size, all.size());
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Courier quotation not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public CourierQuotationDto approve(String id) {
        CourierQuotation entity = findEntityById(id);
        if (entity.getStatus() != CourierQuotation.QuotationStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT quotations can be approved.");
        }
        entity.setStatus(CourierQuotation.QuotationStatus.APPROVED);
        auditUtil.setUpdatedBy(entity);
        return toDto(repository.save(entity));
    }

    @Transactional
    public CourierQuotationDto activate(String id) {
        CourierQuotation entity = findEntityById(id);
        if (entity.getStatus() != CourierQuotation.QuotationStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED quotations can be activated.");
        }
        if (repository.existsByCustomerIdAndStatus(entity.getCustomerId(),
                CourierQuotation.QuotationStatus.ACTIVE)) {
            throw new IllegalStateException(
                    "Customer already has an ACTIVE courier quotation. Expire it first.");
        }
        entity.setStatus(CourierQuotation.QuotationStatus.ACTIVE);
        auditUtil.setUpdatedBy(entity);
        return toDto(repository.save(entity));
    }

    @Transactional
    public CourierQuotationDto expire(String id) {
        CourierQuotation entity = findEntityById(id);
        entity.setStatus(CourierQuotation.QuotationStatus.EXPIRED);
        auditUtil.setUpdatedBy(entity);
        return toDto(repository.save(entity));
    }

    public PageResponse<CourierQuotationDto> searchReport(String customerId, LocalDate startDate, LocalDate endDate,
            String status, int page, int size, String sortBy, String sortDir) {

        String queryCustomerId = (customerId == null || customerId.trim().isEmpty()) ? "null" : customerId;
        String queryStatus = (status == null || status.trim().isEmpty()) ? "null" : status;

        List<CourierQuotation> list = repository.findQuotationsForReport(queryCustomerId, startDate, endDate,
                queryStatus);

        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());

        List<CourierQuotation> pagedList = list.isEmpty() ? list : list.subList(start, end);

        return PaginationUtil.toPageResponse(
                pagedList.stream().map(this::toDto).toList(),
                page, size, list.size());
    }

    private CourierQuotation findEntityById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier quotation not found: " + id));
    }

    private void validateDates(CourierQuotationDto dto) {
        if (dto.getEffectiveDate() != null && dto.getValidTillDate() != null
                && dto.getEffectiveDate().isAfter(dto.getValidTillDate())) {
            throw new IllegalArgumentException("Effective date must be on or before Valid Till date.");
        }
    }

    private String generateNextNumber() {
        List<CourierQuotation> all = repository.findAll();
        int max = 0;
        Pattern p = Pattern.compile("^CQ-(\\d+)$", Pattern.CASE_INSENSITIVE);
        for (CourierQuotation q : all) {
            if (q.getQuotationNumber() != null) {
                Matcher m = p.matcher(q.getQuotationNumber());
                if (m.matches()) {
                    try {
                        max = Math.max(max, Integer.parseInt(m.group(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return "CQ-" + String.format("%04d", max + 1);
    }

    private CourierQuotation toEntity(CourierQuotationDto dto) {
        CourierQuotation entity = new CourierQuotation();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setBranchName(dto.getBranchName());
        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setValidTillDate(dto.getValidTillDate());
        entity.setRemarks(dto.getRemarks());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : CourierQuotation.QuotationStatus.DRAFT);
        entity.setRateType(dto.getRateType());
        entity.setFuelChargePercentage(dto.getFuelChargePercentage());
        entity.setFovCharges(dto.getFovCharges());
        entity.setSelectedBankAccountId(dto.getSelectedBankAccountId());
        entity.setZoneRates(new java.util.ArrayList<>(mapZoneRatesToEntity(dto.getZoneRates())));
        return entity;
    }

    private void updateEntity(CourierQuotation entity, CourierQuotationDto dto) {
        entity.setCustomerId(dto.getCustomerId());
        entity.setBranchName(dto.getBranchName());
        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setValidTillDate(dto.getValidTillDate());
        entity.setRemarks(dto.getRemarks());
        entity.setRateType(dto.getRateType());
        entity.setFuelChargePercentage(dto.getFuelChargePercentage());
        entity.setFovCharges(dto.getFovCharges());
        entity.setSelectedBankAccountId(dto.getSelectedBankAccountId());
        // Replace embedded list so MongoDB persists nested zone rate changes on every update
        entity.setZoneRates(new java.util.ArrayList<>(mapZoneRatesToEntity(dto.getZoneRates())));
    }

    public CourierQuotationDto toDto(CourierQuotation entity) {
        CourierQuotationDto dto = new CourierQuotationDto();
        dto.setId(entity.getId());
        dto.setQuotationNumber(entity.getQuotationNumber());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setBranchName(entity.getBranchName());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setValidTillDate(entity.getValidTillDate());
        dto.setRemarks(entity.getRemarks());
        dto.setStatus(entity.getStatus());
        dto.setRateType(entity.getRateType());
        dto.setFuelChargePercentage(entity.getFuelChargePercentage());
        dto.setFovCharges(entity.getFovCharges());
        dto.setSelectedBankAccountId(entity.getSelectedBankAccountId());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        dto.setZoneRates(mapZoneRatesToDto(entity.getZoneRates()));
        return dto;
    }

    private List<com.app.billing.model.ZoneRateConfig> mapZoneRatesToEntity(
            List<com.app.billing.dto.ZoneRateConfigDto> dtoList) {
        if (dtoList == null)
            return new java.util.ArrayList<>();
        return dtoList.stream().map(d -> com.app.billing.model.ZoneRateConfig.builder()
                .zoneId(d.getZoneId())
                .zoneName(d.getZoneName())
                .expressBaseRate(d.getExpressBaseRate())
                .expressIncrementalRate(d.getExpressIncrementalRate())
                .expressPerKgRate(d.getExpressPerKgRate())
                .surfaceSlab1Rate(d.getSurfaceSlab1Rate())
                .surfaceSlab2Rate(d.getSurfaceSlab2Rate())
                .standardBaseRate3Kg(d.getStandardBaseRate3Kg())
                .standardAdditionalPerKg(d.getStandardAdditionalPerKg())
                .standardRate1Kg(d.getStandardRate1Kg())
                .standardRate2Kg(d.getStandardRate2Kg())
                .standardRate3Kg(d.getStandardRate3Kg())
                .standardRate4Kg(d.getStandardRate4Kg())
                .standardRate5Kg(d.getStandardRate5Kg())
                .standardPerKgAbove3(d.getStandardPerKgAbove3())
                .build()).collect(Collectors.toList());
    }

    private List<com.app.billing.dto.ZoneRateConfigDto> mapZoneRatesToDto(
            List<com.app.billing.model.ZoneRateConfig> entityList) {
        if (entityList == null)
            return new java.util.ArrayList<>();
        return entityList.stream().map(e -> com.app.billing.dto.ZoneRateConfigDto.builder()
                .zoneId(e.getZoneId())
                .zoneName(e.getZoneName())
                .expressBaseRate(e.getExpressBaseRate())
                .expressIncrementalRate(e.getExpressIncrementalRate())
                .expressPerKgRate(e.getExpressPerKgRate())
                .surfaceSlab1Rate(e.getSurfaceSlab1Rate())
                .surfaceSlab2Rate(e.getSurfaceSlab2Rate())
                .standardBaseRate3Kg(e.getStandardBaseRate3Kg())
                .standardAdditionalPerKg(e.getStandardAdditionalPerKg())
                .standardRate1Kg(e.getStandardRate1Kg())
                .standardRate2Kg(e.getStandardRate2Kg())
                .standardRate3Kg(e.getStandardRate3Kg())
                .standardRate4Kg(e.getStandardRate4Kg())
                .standardRate5Kg(e.getStandardRate5Kg())
                .standardPerKgAbove3(e.getStandardPerKgAbove3())
                .build()).collect(Collectors.toList());
    }
}

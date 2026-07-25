package com.app.billing.service;

import com.app.billing.dao.CourierQuotationRepository;
import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dto.AwbShipmentLookupDto;
import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.MonthlyCourierQuotation;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.ClientEntryEditLockService;
import com.app.billing.util.CourierTrackingNumberValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.billing.dto.PageResponse;
import com.app.billing.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyCourierEntryService {

    private final MonthlyCourierEntryRepository repository;
    private final MonthlyCourierQuotationService quotationService;
    private final CourierQuotationRepository courierQuotationRepository;
    private final CourierRateCalculatorService rateCalculatorService;
    private final AuditUtil auditUtil;
    private final GlobalAwbUniquenessService globalAwbUniquenessService;
    private final AwbCenterService awbCenterService;
    private final ClientEntryEditLockService clientEntryEditLockService;

    @Transactional
    public MonthlyCourierEntryDto create(String quotationId, MonthlyCourierEntryDto dto) {
        MonthlyCourierQuotation quotation = quotationService.findEntityById(quotationId);
        clientEntryEditLockService.enforceEditAllowed(quotation.getMonth(), quotation.getYear());

        MonthlyCourierEntry entity = toEntity(dto);
        entity.setMonthlyQuotationId(quotationId);
        validateAndNormalizeTrackingNumber(entity, null, null);

        String zone = (entity.getZone() != null && !entity.getZone().isBlank()) ? entity.getZone() : quotation.getZone();
        calculateAmount(quotation.getCustomerId(), zone, entity);
        auditUtil.setCreatedBy(entity);

        entity = repository.save(entity);
        awbCenterService.markCompletedIfPresent(entity.getTrackingNumber(), "CLIENT_ENTRY", entity.getId());

        quotationService.recalculateTotals(quotationId);

        return toDto(entity);
    }

    @Transactional
    public MonthlyCourierEntryDto update(String id, MonthlyCourierEntryDto dto) {
        MonthlyCourierEntry existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + id));

        MonthlyCourierQuotation quotation = quotationService.findEntityById(existing.getMonthlyQuotationId());
        clientEntryEditLockService.enforceEditAllowed(quotation.getMonth(), quotation.getYear());

        String priorTrackingRaw = existing.getTrackingNumber();
        updateEntity(existing, dto);
        validateAndNormalizeTrackingNumber(existing, existing.getId(), priorTrackingRaw);
        String zone = (existing.getZone() != null && !existing.getZone().isBlank()) ? existing.getZone() : quotation.getZone();
        if (!Boolean.TRUE.equals(existing.getAmountOverridden())) {
            calculateAmount(quotation.getCustomerId(), zone, existing);
        }

        auditUtil.setUpdatedBy(existing);
        existing = repository.save(existing);
        awbCenterService.markCompletedIfPresent(existing.getTrackingNumber(), "CLIENT_ENTRY", existing.getId());

        quotationService.recalculateTotals(existing.getMonthlyQuotationId());

        return toDto(existing);
    }

    public List<MonthlyCourierEntryDto> findByQuotationId(String quotationId) {
        return findByQuotationId(quotationId, null, null);
    }

    public List<MonthlyCourierEntryDto> findByQuotationId(String quotationId, String search, String fields) {
        List<MonthlyCourierEntry> all = repository.findByMonthlyQuotationIdOrderByEntryDateAsc(quotationId);
        return filterAndSearchEntries(all, search, fields).stream().map(this::toDto).collect(Collectors.toList());
    }

    public PageResponse<MonthlyCourierEntryDto> findByQuotationId(String quotationId, int page, int size) {
        return findByQuotationId(quotationId, page, size, null, null);
    }

    public PageResponse<MonthlyCourierEntryDto> findByQuotationId(String quotationId, int page, int size, String search, String fields) {
        List<MonthlyCourierEntry> all = repository.findByMonthlyQuotationIdOrderByEntryDateAsc(quotationId);
        List<MonthlyCourierEntry> filtered = filterAndSearchEntries(all, search, fields);
        long total = filtered.size();
        int from = page * size;
        if (from >= total) {
            return PaginationUtil.toPageResponse(List.of(), page, size, total);
        }
        int to = Math.min(from + size, (int) total);
        List<MonthlyCourierEntryDto> slice = filtered.subList(from, to).stream().map(this::toDto).collect(Collectors.toList());
        return PaginationUtil.toPageResponse(slice, page, size, total);
    }

    List<MonthlyCourierEntry> filterAndSearchEntries(List<MonthlyCourierEntry> list, String search, String fieldsStr) {
        log.info("filterAndSearchEntries called with search: '{}', fieldsStr: '{}'", search, fieldsStr);
        if (search == null || search.trim().isEmpty()) {
            return list;
        }
        String s = search.trim().toLowerCase();
        List<String> fields = new java.util.ArrayList<>();
        if (fieldsStr != null && !fieldsStr.trim().isEmpty()) {
            for (String f : fieldsStr.split(",")) {
                fields.add(f.trim().toLowerCase());
            }
        }
        if (fields.isEmpty()) {
            fields = List.of("date", "couriertype", "trackingnumber", "destination", "weight", "cost", "itemtype", "zone", "ratetype", "description", "consignor", "status");
        }

        final List<String> searchFields = fields;
        log.info("Resolved searchFields: {}", searchFields);
        return list.stream().filter(e -> {
            boolean match = false;
            if (searchFields.contains("date") && e.getEntryDate() != null && e.getEntryDate().toString().contains(s)) {
                match = true;
            }
            if (searchFields.contains("couriertype") && e.getCourierType() != null && e.getCourierType().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("trackingnumber") && e.getTrackingNumber() != null && e.getTrackingNumber().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("destination")) {
                if (e.getConsigneeAddress() != null && e.getConsigneeAddress().toLowerCase().contains(s)) match = true;
                if (e.getReceiverName() != null && e.getReceiverName().toLowerCase().contains(s)) match = true;
                if (e.getPincode() != null && e.getPincode().toLowerCase().contains(s)) match = true;
                if (e.getState() != null && e.getState().toLowerCase().contains(s)) match = true;
                if (e.getAreaName() != null && e.getAreaName().toLowerCase().contains(s)) match = true;
            }
            if (searchFields.contains("weight") && e.getWeight() != null && e.getWeight().toString().contains(s)) {
                match = true;
            }
            if (searchFields.contains("cost") && e.getAmount() != null && e.getAmount().toString().contains(s)) {
                match = true;
            }
            if (searchFields.contains("itemtype") && e.getItemType() != null && e.getItemType().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("zone") && e.getZone() != null && e.getZone().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("ratetype") && e.getRateType() != null && e.getRateType().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("description") && e.getAdditionalChargesDescription() != null && e.getAdditionalChargesDescription().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("consignor") && e.getConsignor() != null && e.getConsignor().toLowerCase().contains(s)) {
                match = true;
            }
            if (searchFields.contains("status") && e.getDeliveryStatus() != null && e.getDeliveryStatus().toLowerCase().contains(s)) {
                match = true;
            }
            return match;
        }).collect(Collectors.toList());
    }

    public AwbShipmentLookupDto lookupByTrackingNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return AwbShipmentLookupDto.builder().found(false).message("Enter AWB number").build();
        }
        String norm = CourierTrackingNumberValidator.normalizeOrNull(raw.trim());
        if (norm == null) {
            return AwbShipmentLookupDto.builder().found(false).message("Invalid AWB format").build();
        }
        Optional<MonthlyCourierEntry> opt = repository.findFirstByTrackingNumberIgnoreCase(norm);
        if (opt.isEmpty()) {
            return AwbShipmentLookupDto.builder().found(false).message("AWB not found").build();
        }
        MonthlyCourierEntry e = opt.get();
        MonthlyCourierQuotation q = quotationService.findEntityById(e.getMonthlyQuotationId());
        String receiver = e.getReceiverName() != null ? e.getReceiverName() : "";
        if (receiver.isEmpty() && e.getConsigneeAddress() != null) {
            receiver = e.getConsigneeAddress();
        }
        LocalDate edd = e.getEntryDate() != null ? e.getEntryDate().plusDays(3) : null;
        return AwbShipmentLookupDto.builder()
                .found(true)
                .message("OK")
                .clientName(q.getCustomerName() != null ? q.getCustomerName() : "")
                .receiverName(receiver)
                .receiverAddress(e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "")
                .status(e.getDeliveryStatus() != null ? e.getDeliveryStatus() : "")
                .weight(e.getWeight())
                .amount(e.getAmount())
                .bookingDate(e.getEntryDate())
                .expectedDeliveryDate(edd)
                .entryId(e.getId())
                .monthlyQuotationId(e.getMonthlyQuotationId())
                .trackingNumber(norm)
                .build();
    }

    @Transactional
    public void delete(String id) {
        MonthlyCourierEntry existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + id));

        MonthlyCourierQuotation quotation = quotationService.findEntityById(existing.getMonthlyQuotationId());
        clientEntryEditLockService.enforceEditAllowed(quotation.getMonth(), quotation.getYear());

        String quotationId = existing.getMonthlyQuotationId();
        repository.delete(existing);

        quotationService.recalculateTotals(quotationId);
    }

    /**
     * Normalizes AWB; validates alphanumeric; if purely numeric, ensures no duplicate on another record.
     * On update, unchanged AWB (same normalized value as before save) skips duplicate checks.
     */
    private void validateAndNormalizeTrackingNumber(MonthlyCourierEntry entity, String excludeEntryId,
            String priorRawTracking) {
        String normalized = CourierTrackingNumberValidator.normalizeOrNull(entity.getTrackingNumber());
        entity.setTrackingNumber(normalized);
        if (normalized == null) {
            return;
        }
        CourierTrackingNumberValidator.validateFormat(normalized);
        String priorNorm = CourierTrackingNumberValidator.normalizeOrNull(priorRawTracking);
        if (excludeEntryId != null && Objects.equals(normalized, priorNorm)) {
            return;
        }
        if (!CourierTrackingNumberValidator.isPurelyNumeric(normalized)) {
            return;
        }
        awbCenterService.assertAwbAllowedForModule(normalized, AwbCenterService.MODULE_CLIENT_ENTRY, null);
        globalAwbUniquenessService.assertAwbAvailableForMonthlyEntry(normalized, excludeEntryId);
    }

    private void calculateAmount(String customerId, String zone, MonthlyCourierEntry entity) {
        // If user manually overrode the amount, skip auto-calculation (manual override allowed)
        if (Boolean.TRUE.equals(entity.getAmountOverridden())) {
            return;
        }

        // Validation: weight and zone required for zone-based calculation
        if (zone == null || zone.isBlank()) {
            return;
        }
        if (entity.getWeight() == null || entity.getWeight() <= 0) {
            return;
        }

        // Dynamic Zone Calculation (cumulative slab model)
        List<CourierQuotation> quotes = courierQuotationRepository.findByCustomerId(customerId);
        CourierQuotation activeQuote = quotes.stream()
                .filter(q -> q.getStatus() == CourierQuotation.QuotationStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (activeQuote != null && activeQuote.getZoneRates() != null) {
            String rateType = entity.getRateType() != null ? entity.getRateType()
                    : (activeQuote.getRateType() != null ? activeQuote.getRateType() : entity.getCourierType());
            Double calculatedAmount = rateCalculatorService.calculateAmount(
                    zone,
                    rateType,
                    entity.getWeight(),
                    activeQuote.getZoneRates());

            if (calculatedAmount > 0) {
                entity.setAmount(calculatedAmount);
                entity.setRate(calculatedAmount);
            }
        }
    }

    /** @param rateType EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass; preferred over courierType for calculation */
    public Double calculateDynamicAmount(String quotationId, String zone, String rateType, String courierType, Double weight) {
        MonthlyCourierQuotation quotation = quotationService.findEntityById(quotationId);
        List<CourierQuotation> quotes = courierQuotationRepository.findByCustomerId(quotation.getCustomerId());
        CourierQuotation activeQuote = quotes.stream()
                .filter(q -> q.getStatus() == CourierQuotation.QuotationStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (activeQuote != null && activeQuote.getZoneRates() != null && zone != null && weight != null) {
            String rt = (rateType != null && !rateType.isBlank()) ? rateType
                    : (activeQuote.getRateType() != null ? activeQuote.getRateType() : courierType);
            return rateCalculatorService.calculateAmount(zone, rt, weight, activeQuote.getZoneRates());
        }
        return 0.0;
    }

    private void validateDestination(MonthlyCourierEntryDto dto) {
        boolean hasReceiver = dto.getReceiverName() != null && !dto.getReceiverName().isBlank();
        boolean hasPin = dto.getPincode() != null && !dto.getPincode().isBlank();
        boolean structured = hasReceiver || hasPin;
        boolean legacy = dto.getConsigneeAddress() != null && !dto.getConsigneeAddress().isBlank();
        if (!structured && !legacy) {
            throw new IllegalArgumentException("Enter receiver / destination details, or legacy consignee address.");
        }
    }

    private String buildConsigneeAddressLine(MonthlyCourierEntry e) {
        if (e.getReceiverName() != null && !e.getReceiverName().isBlank()) {
            StringBuilder sb = new StringBuilder(e.getReceiverName().trim());
            if (e.getFullAddress() != null && !e.getFullAddress().isBlank()) {
                sb.append(", ").append(e.getFullAddress().trim());
            }
            if (e.getAreaName() != null && !e.getAreaName().isBlank()) {
                sb.append(", ").append(e.getAreaName().trim());
            }
            if (e.getDestinationCity() != null && !e.getDestinationCity().isBlank()) {
                sb.append(", ").append(e.getDestinationCity().trim());
            }
            if (e.getState() != null && !e.getState().isBlank()) {
                sb.append(", ").append(e.getState().trim());
            }
            if (e.getPincode() != null && !e.getPincode().isBlank()) {
                sb.append(" - ").append(e.getPincode().trim());
            }
            return sb.toString();
        }
        return e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "";
    }

    private MonthlyCourierEntry toEntity(MonthlyCourierEntryDto dto) {
        validateDestination(dto);
        MonthlyCourierEntry entity = new MonthlyCourierEntry();
        entity.setEntryDate(dto.getEntryDate());
        entity.setConsignor(dto.getConsignor());
        entity.setReceiverName(dto.getReceiverName());
        entity.setPincode(dto.getPincode());
        entity.setAreaName(dto.getAreaName());
        entity.setState(dto.getState());
        entity.setDestinationCity(dto.getDestinationCity());
        entity.setFullAddress(dto.getFullAddress());
        if (dto.getConsigneeAddress() != null) {
            entity.setConsigneeAddress(dto.getConsigneeAddress());
        }
        entity.setConsigneeAddress(buildConsigneeAddressLine(entity));
        entity.setCourierType(dto.getCourierType());
        entity.setWeight(dto.getWeight());
        entity.setTrackingNumber(dto.getTrackingNumber());
        entity.setItemType(dto.getItemType());
        entity.setDeliveryStatus(dto.getDeliveryStatus());

        entity.setZone(dto.getZone());
        entity.setRateType(dto.getRateType());
        entity.setRate(dto.getRate());
        entity.setAmount(dto.getAmount());
        entity.setAmountStatus(dto.getAmountStatus());
        if (dto.getAmountOverridden() != null) {
            entity.setAmountOverridden(dto.getAmountOverridden());
        }
        entity.setAdditionalCharges(dto.getAdditionalCharges());
        entity.setAdditionalChargesDescription(dto.getAdditionalChargesDescription());
        entity.setGstApplicable(dto.getGstApplicable());
        entity.setFuelApplicable(dto.getFuelApplicable());
        entity.setFovApplicable(dto.getFovApplicable());
        return entity;
    }

    private void updateEntity(MonthlyCourierEntry entity, MonthlyCourierEntryDto dto) {
        validateDestination(dto);
        entity.setEntryDate(dto.getEntryDate());
        entity.setConsignor(dto.getConsignor());
        entity.setReceiverName(dto.getReceiverName());
        entity.setPincode(dto.getPincode());
        entity.setAreaName(dto.getAreaName());
        entity.setState(dto.getState());
        entity.setDestinationCity(dto.getDestinationCity());
        entity.setFullAddress(dto.getFullAddress());
        if (dto.getConsigneeAddress() != null) {
            entity.setConsigneeAddress(dto.getConsigneeAddress());
        }
        entity.setConsigneeAddress(buildConsigneeAddressLine(entity));
        entity.setCourierType(dto.getCourierType());
        entity.setWeight(dto.getWeight());
        entity.setTrackingNumber(dto.getTrackingNumber());
        entity.setItemType(dto.getItemType());
        entity.setDeliveryStatus(dto.getDeliveryStatus());

        entity.setZone(dto.getZone());
        entity.setRateType(dto.getRateType());
        entity.setRate(dto.getRate());
        entity.setAmount(dto.getAmount());
        entity.setAmountStatus(dto.getAmountStatus());
        if (dto.getAmountOverridden() != null) {
            entity.setAmountOverridden(dto.getAmountOverridden());
        }
        entity.setAdditionalCharges(dto.getAdditionalCharges());
        entity.setAdditionalChargesDescription(dto.getAdditionalChargesDescription());
        if (dto.getGstApplicable() != null) {
            entity.setGstApplicable(dto.getGstApplicable());
        }
        if (dto.getFuelApplicable() != null) {
            entity.setFuelApplicable(dto.getFuelApplicable());
        }
        if (dto.getFovApplicable() != null) {
            entity.setFovApplicable(dto.getFovApplicable());
        }
        if (dto.getRate() != null) {
            entity.setRate(dto.getRate());
        }
    }

    public MonthlyCourierEntryDto toDto(MonthlyCourierEntry entity) {
        MonthlyCourierEntryDto dto = new MonthlyCourierEntryDto();
        dto.setId(entity.getId());
        dto.setMonthlyQuotationId(entity.getMonthlyQuotationId());
        dto.setEntryDate(entity.getEntryDate());
        dto.setConsignor(entity.getConsignor());
        dto.setReceiverName(entity.getReceiverName());
        dto.setPincode(entity.getPincode());
        dto.setAreaName(entity.getAreaName());
        dto.setState(entity.getState());
        dto.setDestinationCity(entity.getDestinationCity());
        dto.setFullAddress(entity.getFullAddress());
        dto.setConsigneeAddress(entity.getConsigneeAddress());
        dto.setCourierType(entity.getCourierType());
        dto.setWeight(entity.getWeight());
        dto.setTrackingNumber(entity.getTrackingNumber());
        dto.setItemType(entity.getItemType());
        dto.setDeliveryStatus(entity.getDeliveryStatus());

        dto.setZone(entity.getZone());
        dto.setRateType(entity.getRateType());
        dto.setRate(entity.getRate());
        dto.setAmount(entity.getAmount());
        dto.setAmountStatus(entity.getAmountStatus());
        dto.setAmountOverridden(entity.getAmountOverridden());
        dto.setAdditionalCharges(entity.getAdditionalCharges());
        dto.setAdditionalChargesDescription(entity.getAdditionalChargesDescription());
        dto.setGstApplicable(entity.getGstApplicable());
        dto.setFuelApplicable(entity.getFuelApplicable());
        dto.setFovApplicable(entity.getFovApplicable());

        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        return dto;
    }
}

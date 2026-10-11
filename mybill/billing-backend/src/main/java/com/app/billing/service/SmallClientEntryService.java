package com.app.billing.service;

import com.app.billing.dao.CourierQuotationRepository;
import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.dto.AwbShipmentLookupDto;
import com.app.billing.dto.SmallClientEntryDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.model.SmallClientEntryQuotation;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.CourierTrackingNumberValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmallClientEntryService {

    private final SmallClientEntryRepository repository;
    private final SmallClientEntryQuotationService quotationService;
    private final CourierQuotationRepository courierQuotationRepository;
    private final CourierRateCalculatorService rateCalculatorService;
    private final AuditUtil auditUtil;
    private final GlobalAwbUniquenessService globalAwbUniquenessService;
    private final AwbCenterService awbCenterService;

    @Transactional
    public SmallClientEntryDto create(String quotationId, SmallClientEntryDto dto) {
        SmallClientEntryQuotation quotation = quotationService.findEntityById(quotationId);

        SmallClientEntry entity = toEntity(dto);
        entity.setMonthlyQuotationId(quotationId);
        validateAndNormalizeTrackingNumber(entity, null, null);

        String zone = (entity.getZone() != null && !entity.getZone().isBlank()) ? entity.getZone() : quotation.getZone();
        calculateAmount(quotation.getCustomerId(), zone, entity);
        auditUtil.setCreatedBy(entity);

        entity = repository.save(entity);
        awbCenterService.markCompletedIfPresent(entity.getTrackingNumber(), "SMALL_CLIENT_ENTRY", entity.getId());

        quotationService.recalculateTotals(quotationId);

        return toDto(entity);
    }

    @Transactional
    public SmallClientEntryDto update(String id, SmallClientEntryDto dto) {
        SmallClientEntry existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + id));

        SmallClientEntryQuotation quotation = quotationService.findEntityById(existing.getMonthlyQuotationId());

        String priorTrackingRaw = existing.getTrackingNumber();
        updateEntity(existing, dto);
        validateAndNormalizeTrackingNumber(existing, existing.getId(), priorTrackingRaw);
        String zone = (existing.getZone() != null && !existing.getZone().isBlank()) ? existing.getZone() : quotation.getZone();
        if (!Boolean.TRUE.equals(existing.getAmountOverridden())) {
            calculateAmount(quotation.getCustomerId(), zone, existing);
        }

        auditUtil.setUpdatedBy(existing);
        existing = repository.save(existing);
        awbCenterService.markCompletedIfPresent(existing.getTrackingNumber(), "SMALL_CLIENT_ENTRY", existing.getId());

        quotationService.recalculateTotals(existing.getMonthlyQuotationId());

        return toDto(existing);
    }

    public List<SmallClientEntryDto> findByQuotationId(String quotationId) {
        return repository.findByMonthlyQuotationIdOrderByEntryDateAsc(quotationId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public AwbShipmentLookupDto lookupByTrackingNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return AwbShipmentLookupDto.builder().found(false).message("Enter AWB number").build();
        }
        String norm = CourierTrackingNumberValidator.normalizeOrNull(raw.trim());
        if (norm == null) {
            return AwbShipmentLookupDto.builder().found(false).message("Invalid AWB format").build();
        }
        Optional<SmallClientEntry> opt = repository.findFirstByTrackingNumberIgnoreCase(norm);
        if (opt.isEmpty()) {
            return AwbShipmentLookupDto.builder().found(false).message("AWB not found").build();
        }
        SmallClientEntry e = opt.get();
        SmallClientEntryQuotation q = quotationService.findEntityById(e.getMonthlyQuotationId());
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
        SmallClientEntry existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found: " + id));

        SmallClientEntryQuotation quotation = quotationService.findEntityById(existing.getMonthlyQuotationId());

        String quotationId = existing.getMonthlyQuotationId();
        repository.delete(existing);

        quotationService.recalculateTotals(quotationId);
    }

    /**
     * Normalizes AWB; validates alphanumeric; if purely numeric, ensures no duplicate on another record.
     * On update, unchanged AWB (same normalized value as before save) skips duplicate checks.
     */
    private void validateAndNormalizeTrackingNumber(SmallClientEntry entity, String excludeEntryId,
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
        awbCenterService.assertAwbAllowedForModule(normalized, AwbCenterService.MODULE_SMALL_CLIENT_ENTRY, null);
        globalAwbUniquenessService.assertAwbAvailableForSmallClientEntry(normalized, excludeEntryId);
    }

    private void calculateAmount(String customerId, String zone, SmallClientEntry entity) {
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
        SmallClientEntryQuotation quotation = quotationService.findEntityById(quotationId);
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

    private void validateDestination(SmallClientEntryDto dto) {
        boolean hasReceiver = dto.getReceiverName() != null && !dto.getReceiverName().isBlank();
        boolean hasPin = dto.getPincode() != null && !dto.getPincode().isBlank();
        boolean structured = hasReceiver || hasPin;
        boolean legacy = dto.getConsigneeAddress() != null && !dto.getConsigneeAddress().isBlank();
        if (!structured && !legacy) {
            throw new IllegalArgumentException("Enter receiver / destination details, or legacy consignee address.");
        }
    }

    private String buildConsigneeAddressLine(SmallClientEntry e) {
        if (e.getReceiverName() != null && !e.getReceiverName().isBlank()) {
            StringBuilder sb = new StringBuilder(e.getReceiverName().trim());
            if (e.getReceiverPhoneNo() != null && !e.getReceiverPhoneNo().isBlank()) {
                sb.append(" (Ph: ").append(e.getReceiverPhoneNo().trim()).append(")");
            }
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

    private SmallClientEntry toEntity(SmallClientEntryDto dto) {
        validateDestination(dto);
        SmallClientEntry entity = new SmallClientEntry();
        entity.setEntryDate(dto.getEntryDate());
        entity.setConsignor(dto.getConsignor());
        entity.setReceiverName(dto.getReceiverName());
        entity.setReceiverPhoneNo(dto.getReceiverPhoneNo());
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

    private void updateEntity(SmallClientEntry entity, SmallClientEntryDto dto) {
        validateDestination(dto);
        entity.setEntryDate(dto.getEntryDate());
        entity.setConsignor(dto.getConsignor());
        entity.setReceiverName(dto.getReceiverName());
        entity.setReceiverPhoneNo(dto.getReceiverPhoneNo());
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
    }

    public SmallClientEntryDto toDto(SmallClientEntry entity) {
        SmallClientEntryDto dto = new SmallClientEntryDto();
        dto.setId(entity.getId());
        dto.setMonthlyQuotationId(entity.getMonthlyQuotationId());
        dto.setEntryDate(entity.getEntryDate());
        dto.setConsignor(entity.getConsignor());
        dto.setReceiverName(entity.getReceiverName());
        dto.setReceiverPhoneNo(entity.getReceiverPhoneNo());
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


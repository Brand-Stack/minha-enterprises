package com.app.billing.service;

import com.app.billing.dao.CashBookingRepository;
import com.app.billing.dto.CashBookingDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CashBooking;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.CourierTrackingNumberValidator;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashBookingService {

    private final CashBookingRepository repository;
    private final MongoTemplate mongoTemplate;
    private final AuditUtil auditUtil;
    private final GlobalAwbUniquenessService globalAwbUniquenessService;
    private final AwbCenterService awbCenterService;

    @Transactional
    public CashBookingDto create(CashBookingDto dto) {
        normalizeDtoStrings(dto);
        CashBooking e = toEntity(dto);
        validateAwb(e, null, null);
        auditUtil.setCreatedBy(e);
        e = repository.save(e);
        awbCenterService.markCompletedIfPresent(e.getAwbNo(), "CASH_BOOKING", e.getId());
        return toDto(e);
    }

    @Transactional
    public CashBookingDto update(String id, CashBookingDto dto) {
        normalizeDtoStrings(dto);
        CashBooking e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash booking not found: " + id));
        String priorNorm = CourierTrackingNumberValidator.normalizeOrNull(e.getAwbNo());
        applyDto(e, dto);
        validateAwb(e, id, priorNorm);
        auditUtil.setUpdatedBy(e);
        e = repository.save(e);
        awbCenterService.markCompletedIfPresent(e.getAwbNo(), "CASH_BOOKING", e.getId());
        return toDto(e);
    }

    public CashBookingDto findById(String id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Cash booking not found: " + id));
    }

    @Transactional
    public void delete(String id) {
        CashBooking e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash booking not found: " + id));
        repository.delete(e);
    }

    public PageResponse<CashBookingDto> search(
            LocalDate dateFrom,
            LocalDate dateTo,
            String awbNo,
            String receiverName,
            String pincode,
            String state,
            String areaName,
            String courier,
            String status,
            String amountStatus,
            String remarks,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        Query q = buildSearchQuery(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, remarks);
        long total = mongoTemplate.count(q, CashBooking.class);
        q.with(resolveSort(sortBy, sortDir));
        q.skip((long) page * size).limit(size);
        List<CashBookingDto> rows = mongoTemplate.find(q, CashBooking.class).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PaginationUtil.toPageResponse(rows, page, size, (int) total);
    }

    public SearchTotals reportTotals(
            LocalDate dateFrom,
            LocalDate dateTo,
            String awbNo,
            String receiverName,
            String pincode,
            String state,
            String areaName,
            String courier,
            String status,
            String amountStatus,
            String remarks) {
        Query q = buildSearchQuery(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, remarks);
        List<CashBooking> all = mongoTemplate.find(q, CashBooking.class);
        double sum = all.stream().mapToDouble(x -> x.getAmount() != null ? x.getAmount() : 0d).sum();
        return new SearchTotals(all.size(), sum);
    }

    public record SearchTotals(int totalRecords, double totalAmount) {}

    private static java.util.Date toStartOfDayDate(LocalDate localDate) {
        if (localDate == null) return null;
        return java.util.Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
    }

    private static java.util.Date toEndOfDayDate(LocalDate localDate) {
        if (localDate == null) return null;
        return java.util.Date.from(localDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().minusMillis(1));
    }

    private Query buildSearchQuery(
            LocalDate dateFrom,
            LocalDate dateTo,
            String awbNo,
            String receiverName,
            String pincode,
            String state,
            String areaName,
            String courier,
            String status,
            String amountStatus,
            String remarks) {
        Query q = new Query();
        java.util.Date start = toStartOfDayDate(dateFrom);
        java.util.Date end = toEndOfDayDate(dateTo);
        if (start != null && end != null) {
            q.addCriteria(Criteria.where("bookingDate").gte(start).lte(end));
        } else if (start != null) {
            q.addCriteria(Criteria.where("bookingDate").gte(start));
        } else if (end != null) {
            q.addCriteria(Criteria.where("bookingDate").lte(end));
        }
        if (StringUtils.hasText(awbNo)) {
            q.addCriteria(Criteria.where("awbNo").regex("^" + Pattern.quote(awbNo.trim()) + "$", "i"));
        }
        if (StringUtils.hasText(receiverName)) {
            q.addCriteria(Criteria.where("receiverName").regex(".*" + Pattern.quote(receiverName.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(pincode)) {
            q.addCriteria(Criteria.where("pincode").is(pincode.trim()));
        }
        if (StringUtils.hasText(state)) {
            q.addCriteria(Criteria.where("state").regex(".*" + Pattern.quote(state.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(areaName)) {
            q.addCriteria(Criteria.where("areaName").regex(".*" + Pattern.quote(areaName.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(courier)) {
            q.addCriteria(Criteria.where("courier").regex(".*" + Pattern.quote(courier.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(status)) {
            q.addCriteria(Criteria.where("status").regex(".*" + Pattern.quote(status.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(amountStatus)) {
            String esc = Pattern.quote(amountStatus.trim());
            q.addCriteria(Criteria.where("amountStatus").regex("^" + esc + "$", "i"));
        }
        if (StringUtils.hasText(remarks)) {
            q.addCriteria(Criteria.where("remarks").regex(".*" + Pattern.quote(remarks.trim()) + ".*", "i"));
        }
        return q;
    }

    private static Sort resolveSort(String sortBy, String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "bookingDate";
        if (StringUtils.hasText(sortBy)) {
            String s = sortBy.trim();
            if (Set.of("bookingDate", "createdAt", "receiverName", "awbNo", "amount", "status", "amountStatus",
                    "courier", "pincode", "weight").contains(s)) {
                field = s;
            }
        }
        Sort primary = Sort.by(dir, field);
        if ("bookingDate".equals(field)) {
            return primary.and(Sort.by(dir, "createdAt"));
        }
        return primary.and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private void validateAwb(CashBooking e, String excludeId, String priorNorm) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(e.getAwbNo());
        e.setAwbNo(norm);
        if (norm == null) {
            return;
        }
        CourierTrackingNumberValidator.validateFormat(norm);
        if (excludeId != null && Objects.equals(norm, priorNorm)) {
            return;
        }
        if (CourierTrackingNumberValidator.isPurelyNumeric(norm)) {
            awbCenterService.assertAwbAllowedForModule(norm, AwbCenterService.MODULE_CASH_BOOKING, null);
        }
        globalAwbUniquenessService.assertAwbAvailableForCashBooking(norm, excludeId);
    }

    private CashBooking toEntity(CashBookingDto dto) {
        return CashBooking.builder()
                .bookingDate(dto.getBookingDate() != null ? dto.getBookingDate() : LocalDate.now())
                .receiverName(trim(dto.getReceiverName()))
                .pincode(trim(dto.getPincode()))
                .state(trim(dto.getState()))
                .city(trim(dto.getCity()))
                .areaName(trim(dto.getAreaName()))
                .fullAddress(trim(dto.getFullAddress()))
                .awbNo(CourierTrackingNumberValidator.normalizeOrNull(dto.getAwbNo()))
                .courier(trim(dto.getCourier()))
                .weight(dto.getWeight())
                .item(trim(dto.getItem()))
                .status(trim(dto.getStatus()))
                .amount(dto.getAmount())
                .amountStatus(trim(dto.getAmountStatus()))
                .remarks(trim(dto.getRemarks()))
                .build();
    }

    private void applyDto(CashBooking e, CashBookingDto dto) {
        if (dto.getBookingDate() != null) {
            e.setBookingDate(dto.getBookingDate());
        }
        e.setReceiverName(trim(dto.getReceiverName()));
        e.setPincode(trim(dto.getPincode()));
        e.setState(trim(dto.getState()));
        e.setCity(trim(dto.getCity()));
        e.setAreaName(trim(dto.getAreaName()));
        e.setFullAddress(trim(dto.getFullAddress()));
        e.setAwbNo(CourierTrackingNumberValidator.normalizeOrNull(dto.getAwbNo()));
        e.setCourier(trim(dto.getCourier()));
        e.setWeight(dto.getWeight());
        e.setItem(trim(dto.getItem()));
        e.setStatus(trim(dto.getStatus()));
        e.setAmount(dto.getAmount());
        e.setAmountStatus(trim(dto.getAmountStatus()));
        e.setRemarks(trim(dto.getRemarks()));
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }

    private static void normalizeDtoStrings(CashBookingDto dto) {
        dto.setReceiverName(trim(dto.getReceiverName()));
        dto.setPincode(trim(dto.getPincode()));
        dto.setState(trim(dto.getState()));
        dto.setCity(trim(dto.getCity()));
        dto.setAreaName(trim(dto.getAreaName()));
        dto.setFullAddress(trim(dto.getFullAddress()));
        dto.setAwbNo(dto.getAwbNo() != null ? dto.getAwbNo().trim() : null);
        dto.setCourier(trim(dto.getCourier()));
        dto.setItem(trim(dto.getItem()));
        dto.setStatus(trim(dto.getStatus()));
        dto.setAmountStatus(trim(dto.getAmountStatus()));
        dto.setRemarks(trim(dto.getRemarks()));
    }

    private CashBookingDto toDto(CashBooking e) {
        return CashBookingDto.builder()
                .id(e.getId())
                .bookingDate(e.getBookingDate())
                .receiverName(e.getReceiverName())
                .pincode(e.getPincode())
                .state(e.getState())
                .city(e.getCity())
                .areaName(e.getAreaName())
                .fullAddress(e.getFullAddress())
                .awbNo(e.getAwbNo())
                .courier(e.getCourier())
                .weight(e.getWeight())
                .item(e.getItem())
                .status(e.getStatus())
                .amount(e.getAmount())
                .amountStatus(e.getAmountStatus())
                .remarks(e.getRemarks())
                .lastUpdatedBy(e.getLastUpdatedBy())
                .build();
    }

    @Transactional
    public CashBookingDto patchStatus(String id, String amountStatus, String remarks) {
        CashBooking e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash booking not found: " + id));
        if (amountStatus != null) {
            e.setAmountStatus(amountStatus);
        }
        if (remarks != null) {
            e.setRemarks(remarks);
        }
        auditUtil.setUpdatedBy(e);
        e = repository.save(e);
        return toDto(e);
    }
}

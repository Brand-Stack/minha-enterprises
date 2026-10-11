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
            String paymentMode,
            String remarks,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        Query q = buildSearchQuery(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, paymentMode, remarks);
        long total = mongoTemplate.count(q, CashBooking.class);
        q.with(resolveSort(sortBy, sortDir));
        q.skip((long) page * size).limit(size);
        List<CashBookingDto> rows = mongoTemplate.find(q, CashBooking.class).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PaginationUtil.toPageResponse(rows, page, size, (int) total);
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
        return search(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, null, remarks, page, size, sortBy, sortDir);
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
            String paymentMode,
            String remarks) {
        Query q = buildSearchQuery(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, paymentMode, remarks);
        List<CashBooking> all = mongoTemplate.find(q, CashBooking.class);
        double sumAmount = 0.0;
        double sumReceived = 0.0;
        double sumPending = 0.0;

        for (CashBooking x : all) {
            double amt = x.getAmount() != null ? x.getAmount() : 0.0;
            amt = Math.round(amt * 100.0) / 100.0;
            sumAmount += amt;

            String st = x.getAmountStatus() != null ? x.getAmountStatus().trim() : "";
            boolean isPaidStatus = "Paid".equalsIgnoreCase(st) || "Cash".equalsIgnoreCase(st) || "GPay".equalsIgnoreCase(st) || "COD".equalsIgnoreCase(st);

            double rec;
            if (x.getReceivedAmount() != null) {
                rec = Math.round(x.getReceivedAmount() * 100.0) / 100.0;
            } else if (isPaidStatus) {
                rec = amt;
            } else {
                rec = 0.0;
            }
            sumReceived += rec;

            double pend;
            if (isPaidStatus) {
                pend = 0.0;
            } else {
                pend = Math.round(Math.max(0.0, amt - rec) * 100.0) / 100.0;
            }
            sumPending += pend;
        }

        sumAmount = Math.round(sumAmount * 100.0) / 100.0;
        sumReceived = Math.round(sumReceived * 100.0) / 100.0;
        sumPending = Math.round(sumPending * 100.0) / 100.0;

        return new SearchTotals(all.size(), sumAmount, sumReceived, sumPending);
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
        return reportTotals(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, null, remarks);
    }

    public record SearchTotals(int totalRecords, double totalAmount, double totalReceivedAmount, double totalPendingAmount) {}

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
            String paymentMode,
            String remarks) {
        Query q = new Query();
        if (dateFrom != null && dateTo != null) {
            q.addCriteria(Criteria.where("bookingDate").gte(dateFrom).lte(dateTo));
        } else if (dateFrom != null) {
            q.addCriteria(Criteria.where("bookingDate").gte(dateFrom));
        } else if (dateTo != null) {
            q.addCriteria(Criteria.where("bookingDate").lte(dateTo));
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
        if (StringUtils.hasText(paymentMode)) {
            String esc = Pattern.quote(paymentMode.trim());
            q.addCriteria(Criteria.where("paymentMode").regex("^" + esc + "$", "i"));
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

    private void validatePhoneNumbers(List<String> phoneNumbers, String fieldName) {
        if (phoneNumbers == null) return;
        for (String p : phoneNumbers) {
            if (p != null && !p.isBlank()) {
                String trimmed = p.trim();
                if (!trimmed.matches("^\\d{10}$")) {
                    throw new IllegalArgumentException("Invalid " + fieldName + " '" + trimmed + "': phone number must be exactly 10 digits.");
                }
            }
        }
    }

    private CashBooking toEntity(CashBookingDto dto) {
        validatePhoneNumbers(dto.getFromPhoneNumbers(), "From Phone Number");
        validatePhoneNumbers(dto.getToPhoneNumbers(), "To Phone Number");

        CashBooking e = CashBooking.builder()
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
                .paymentMode(trim(dto.getPaymentMode()))
                .otherPaymentMode(trim(dto.getOtherPaymentMode()))
                .remarks(trim(dto.getRemarks()))
                .build();

        if (dto.getFromPhoneNumbers() != null && !dto.getFromPhoneNumbers().isEmpty()) {
            e.setFromPhoneNumbers(dto.getFromPhoneNumbers().stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        } else if (dto.getFromPhone() != null && !dto.getFromPhone().isBlank()) {
            e.setFromPhone(dto.getFromPhone());
        }

        if (dto.getToPhoneNumbers() != null && !dto.getToPhoneNumbers().isEmpty()) {
            e.setToPhoneNumbers(dto.getToPhoneNumbers().stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        } else if (dto.getToPhone() != null && !dto.getToPhone().isBlank()) {
            e.setToPhone(dto.getToPhone());
        }

        calculateAndValidateAmounts(e, dto);
        return e;
    }

    private void applyDto(CashBooking e, CashBookingDto dto) {
        validatePhoneNumbers(dto.getFromPhoneNumbers(), "From Phone Number");
        validatePhoneNumbers(dto.getToPhoneNumbers(), "To Phone Number");

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
        if (dto.getAmountStatus() != null) {
            e.setAmountStatus(trim(dto.getAmountStatus()));
        }
        if (dto.getPaymentMode() != null) {
            e.setPaymentMode(trim(dto.getPaymentMode()));
        }
        if (dto.getOtherPaymentMode() != null) {
            e.setOtherPaymentMode(trim(dto.getOtherPaymentMode()));
        }
        e.setRemarks(trim(dto.getRemarks()));

        if (dto.getFromPhoneNumbers() != null && !dto.getFromPhoneNumbers().isEmpty()) {
            e.setFromPhoneNumbers(dto.getFromPhoneNumbers().stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        } else if (dto.getFromPhone() != null && !dto.getFromPhone().isBlank()) {
            e.setFromPhone(dto.getFromPhone());
        }

        if (dto.getToPhoneNumbers() != null && !dto.getToPhoneNumbers().isEmpty()) {
            e.setToPhoneNumbers(dto.getToPhoneNumbers().stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        } else if (dto.getToPhone() != null && !dto.getToPhone().isBlank()) {
            e.setToPhone(dto.getToPhone());
        }

        calculateAndValidateAmounts(e, dto);
    }

    private void calculateAndValidateAmounts(CashBooking e, CashBookingDto dto) {
        double originalAmount = e.getAmount() != null ? e.getAmount() : 0.0;
        originalAmount = Math.round(originalAmount * 100.0) / 100.0;
        e.setAmount(originalAmount);

        Double received = dto != null ? dto.getReceivedAmount() : null;
        if (received == null && dto == null) {
            received = e.getReceivedAmount();
        }

        if (received != null) {
            received = Math.round(received * 100.0) / 100.0;
            if (received < 0) {
                throw new IllegalArgumentException("Received amount cannot be negative");
            }
            if (received > originalAmount && originalAmount > 0) {
                throw new IllegalArgumentException("Received amount (₹" + String.format("%.2f", received) + ") cannot exceed total amount (₹" + String.format("%.2f", originalAmount) + ")");
            }
            double pending = Math.round(Math.max(0.0, originalAmount - received) * 100.0) / 100.0;
            e.setReceivedAmount(received);
            e.setPendingAmount(pending);

            if (dto != null && StringUtils.hasText(dto.getAmountStatus())) {
                e.setAmountStatus(dto.getAmountStatus().trim());
            } else {
                if (pending <= 0.0 && originalAmount > 0.0) {
                    e.setAmountStatus("Paid");
                } else if (received > 0.0 && pending > 0.0) {
                    e.setAmountStatus("Partial");
                } else if (received <= 0.0) {
                    e.setAmountStatus("Pending");
                }
            }
        } else {
            if (dto != null && StringUtils.hasText(dto.getAmountStatus())) {
                e.setAmountStatus(dto.getAmountStatus().trim());
            }
            String st = e.getAmountStatus();
            if ("Paid".equalsIgnoreCase(st) || "Cash".equalsIgnoreCase(st) || "GPay".equalsIgnoreCase(st) || "COD".equalsIgnoreCase(st)) {
                e.setReceivedAmount(originalAmount);
                e.setPendingAmount(0.0);
            } else if ("Partial".equalsIgnoreCase(st)) {
                double rec = e.getReceivedAmount() != null ? Math.round(e.getReceivedAmount() * 100.0) / 100.0 : 0.0;
                e.setReceivedAmount(rec);
                e.setPendingAmount(Math.round(Math.max(0.0, originalAmount - rec) * 100.0) / 100.0);
            } else {
                e.setReceivedAmount(0.0);
                e.setPendingAmount(originalAmount);
            }
        }
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
        dto.setPaymentMode(trim(dto.getPaymentMode()));
        dto.setOtherPaymentMode(trim(dto.getOtherPaymentMode()));
        dto.setRemarks(trim(dto.getRemarks()));
    }

    private CashBookingDto toDto(CashBooking e) {
        double originalAmount = e.getAmount() != null ? e.getAmount() : 0.0;
        originalAmount = Math.round(originalAmount * 100.0) / 100.0;
        String st = e.getAmountStatus();
        Double received = e.getReceivedAmount();
        if (received == null) {
            if ("Paid".equalsIgnoreCase(st) || "Cash".equalsIgnoreCase(st) || "GPay".equalsIgnoreCase(st) || "COD".equalsIgnoreCase(st)) {
                received = originalAmount;
            } else {
                received = 0.0;
            }
        }
        received = Math.round(received * 100.0) / 100.0;
        Double pending = Math.round(Math.max(0.0, originalAmount - received) * 100.0) / 100.0;

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
                .receivedAmount(received)
                .pendingAmount(pending)
                .amountStatus(e.getAmountStatus())
                .paymentMode(e.getPaymentMode())
                .otherPaymentMode(e.getOtherPaymentMode())
                .fromPhoneNumbers(e.getFromPhoneNumbers())
                .toPhoneNumbers(e.getToPhoneNumbers())
                .fromPhone(e.getFromPhone())
                .toPhone(e.getToPhone())
                .remarks(e.getRemarks())
                .lastUpdatedBy(e.getLastUpdatedBy())
                .build();
    }

    @Transactional
    public CashBookingDto patchStatus(String id, String amountStatus, Double receivedAmount, String remarks) {
        return patchStatus(id, amountStatus, receivedAmount, remarks, null, null);
    }

    @Transactional
    public CashBookingDto patchStatus(String id, String amountStatus, Double receivedAmount, String remarks, String paymentMode, String otherPaymentMode) {
        CashBooking e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash booking not found: " + id));
        if (amountStatus != null) {
            e.setAmountStatus(amountStatus);
        }
        if (paymentMode != null) {
            e.setPaymentMode(paymentMode);
        }
        if (otherPaymentMode != null) {
            e.setOtherPaymentMode(otherPaymentMode);
        }
        if (remarks != null) {
            e.setRemarks(remarks);
        }
        if (receivedAmount != null) {
            CashBookingDto tempDto = CashBookingDto.builder()
                    .receivedAmount(receivedAmount)
                    .amountStatus(e.getAmountStatus())
                    .build();
            calculateAndValidateAmounts(e, tempDto);
        } else {
            calculateAndValidateAmounts(e, null);
        }
        auditUtil.setUpdatedBy(e);
        e = repository.save(e);
        return toDto(e);
    }
}

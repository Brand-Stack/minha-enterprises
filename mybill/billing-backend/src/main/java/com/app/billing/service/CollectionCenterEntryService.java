package com.app.billing.service;

import com.app.billing.dao.CollectionCenterEntryRepository;
import com.app.billing.dao.CollectionCustomerRepository;
import com.app.billing.dto.CollectionCenterEntryDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CollectionCenterEntry;
import com.app.billing.model.CollectionCustomer;
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
public class CollectionCenterEntryService {

    private final CollectionCenterEntryRepository repository;
    private final CollectionCustomerRepository customerRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditUtil auditUtil;
    private final GlobalAwbUniquenessService globalAwbUniquenessService;
    private final CollectionCustomerAwbService collectionCustomerAwbService;
    private final AwbCenterService awbCenterService;

    @Transactional
    public CollectionCenterEntryDto create(CollectionCenterEntryDto dto) {
        CollectionCustomer parent = customerRepository.findById(dto.getCollectionCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
        if (Boolean.FALSE.equals(parent.getActive())) {
            throw new IllegalArgumentException("Collection customer is inactive.");
        }
        CollectionCenterEntry e = toEntity(dto);
        e.setCustomerName(parent.getCustomerName());
        validateAwb(e, dto, null, null);
        auditUtil.setCreatedBy(e);
        e = repository.save(e);
        linkRegistryAfterSave(e, dto);
        awbCenterService.markCompletedIfPresent(e.getAwbNo(), "COLLECTION_CENTER", e.getId());
        return toDto(e);
    }

    @Transactional
    public CollectionCenterEntryDto update(String id, CollectionCenterEntryDto dto) {
        CollectionCenterEntry e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection center entry not found: " + id));
        String oldNorm = CourierTrackingNumberValidator.normalizeOrNull(e.getAwbNo());
        if (dto.getCollectionCustomerId() != null && !dto.getCollectionCustomerId().equals(e.getCollectionCustomerId())) {
            CollectionCustomer parent = customerRepository.findById(dto.getCollectionCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
            if (Boolean.FALSE.equals(parent.getActive())) {
                throw new IllegalArgumentException("Collection customer is inactive.");
            }
            e.setCollectionCustomerId(dto.getCollectionCustomerId());
            e.setCustomerName(parent.getCustomerName());
        }
        applyDto(e, dto);
        String cid = e.getCollectionCustomerId();
        if (cid != null) {
            final CollectionCenterEntry entryForCustomerName = e;
            customerRepository.findById(cid)
                    .ifPresent(p -> entryForCustomerName.setCustomerName(p.getCustomerName()));
        }
        String newNorm = CourierTrackingNumberValidator.normalizeOrNull(e.getAwbNo());
        boolean awbChanged = !Objects.equals(oldNorm, newNorm);
        if (awbChanged) {
            collectionCustomerAwbService.releaseByEntryId(id);
        }
        validateAwb(e, dto, id, oldNorm);
        auditUtil.setUpdatedBy(e);
        e = repository.save(e);
        if (newNorm != null && awbChanged) {
            linkRegistryAfterSave(e, dto);
        }
        awbCenterService.markCompletedIfPresent(e.getAwbNo(), "COLLECTION_CENTER", e.getId());
        return toDto(e);
    }

    public CollectionCenterEntryDto findById(String id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Collection center entry not found: " + id));
    }

    public List<CollectionCenterEntryDto> findByCustomerId(String customerId) {
        return repository.findByCollectionCustomerIdOrderByEntryDateDesc(customerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public PageResponse<CollectionCenterEntryDto> search(
            String collectionCustomerId,
            String customerNameContains,
            String awbNo,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String amountStatus,
            String paymentMode,
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        Query q = buildSearchQuery(collectionCustomerId, customerNameContains, awbNo, dateFrom, dateTo,
                status, amountStatus, paymentMode, pincode, courier, entryMonth, entryYear);
        long total = mongoTemplate.count(q, CollectionCenterEntry.class);
        q.with(resolveCollectionEntrySort(sortBy, sortDir));
        q.skip((long) page * size).limit(size);
        List<CollectionCenterEntryDto> rows = mongoTemplate.find(q, CollectionCenterEntry.class).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PaginationUtil.toPageResponse(rows, page, size, (int) total);
    }

    public PageResponse<CollectionCenterEntryDto> search(
            String collectionCustomerId,
            String customerNameContains,
            String awbNo,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String amountStatus,
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        return search(collectionCustomerId, customerNameContains, awbNo, dateFrom, dateTo,
                status, amountStatus, null, pincode, courier, entryMonth, entryYear, page, size, sortBy, sortDir);
    }

    private static Sort resolveCollectionEntrySort(String sortBy, String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "entryDate";
        if (StringUtils.hasText(sortBy)) {
            String s = sortBy.trim();
            if (Set.of("entryDate", "createdAt", "customerName", "awbNo", "amount", "status", "amountStatus", "courier",
                    "receiverName", "pincode", "weight").contains(s)) {
                field = s;
            }
        }
        Sort primary = Sort.by(dir, field);
        if ("entryDate".equals(field)) {
            return primary.and(Sort.by(dir, "createdAt"));
        }
        return primary.and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public SearchTotals reportTotals(
            String collectionCustomerId,
            String customerNameContains,
            String awbNo,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String amountStatus,
            String paymentMode,
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear) {
        Query q = buildSearchQuery(collectionCustomerId, customerNameContains, awbNo, dateFrom, dateTo,
                status, amountStatus, paymentMode, pincode, courier, entryMonth, entryYear);
        List<CollectionCenterEntry> all = mongoTemplate.find(q, CollectionCenterEntry.class);
        double sumAmount = 0.0;
        double sumReceived = 0.0;
        double sumPending = 0.0;
        for (CollectionCenterEntry x : all) {
            double amt = x.getAmount() != null ? x.getAmount() : 0.0;
            amt = Math.round(amt * 100.0) / 100.0;
            sumAmount += amt;

            String st = x.getAmountStatus();
            boolean isPaidStatus = st != null && Set.of("PAID", "CASH", "GPAY", "COD").contains(st.trim().toUpperCase());

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
            String collectionCustomerId,
            String customerNameContains,
            String awbNo,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String amountStatus,
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear) {
        return reportTotals(collectionCustomerId, customerNameContains, awbNo, dateFrom, dateTo,
                status, amountStatus, null, pincode, courier, entryMonth, entryYear);
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
            String collectionCustomerId,
            String customerNameContains,
            String awbNo,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String amountStatus,
            String paymentMode,
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear) {
        Query q = new Query();
        if (StringUtils.hasText(collectionCustomerId)) {
            q.addCriteria(Criteria.where("collectionCustomerId").is(collectionCustomerId.trim()));
        }
        if (StringUtils.hasText(customerNameContains)) {
            q.addCriteria(Criteria.where("customerName").regex(".*" + Pattern.quote(customerNameContains.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(awbNo)) {
            q.addCriteria(Criteria.where("awbNo").regex("^" + Pattern.quote(awbNo.trim()) + "$", "i"));
        }
        java.util.Date start = toStartOfDayDate(dateFrom);
        java.util.Date end = toEndOfDayDate(dateTo);
        if (start != null && end != null) {
            q.addCriteria(Criteria.where("entryDate").gte(start).lte(end));
        } else if (start != null) {
            q.addCriteria(Criteria.where("entryDate").gte(start));
        } else if (end != null) {
            q.addCriteria(Criteria.where("entryDate").lte(end));
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
        if (StringUtils.hasText(pincode)) {
            q.addCriteria(Criteria.where("pincode").is(pincode.trim()));
        }
        if (StringUtils.hasText(courier)) {
            q.addCriteria(Criteria.where("courier").regex(".*" + Pattern.quote(courier.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(entryMonth)) {
            q.addCriteria(Criteria.where("entryMonth").is(entryMonth.trim()));
        }
        if (entryYear != null) {
            q.addCriteria(Criteria.where("entryYear").is(entryYear));
        }
        return q;
    }

    @Transactional
    public CollectionCenterEntryDto patchStatus(String id, String amountStatus, Double receivedAmount, String remarks) {
        return patchStatus(id, amountStatus, receivedAmount, remarks, null, null);
    }

    @Transactional
    public CollectionCenterEntryDto patchStatus(String id, String amountStatus, Double receivedAmount, String remarks, String paymentMode, String otherPaymentMode) {
        CollectionCenterEntry e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection center entry not found: " + id));
        if (remarks != null) {
            e.setRemarks(remarks);
        }
        if (paymentMode != null) {
            e.setPaymentMode(paymentMode);
        }
        if (otherPaymentMode != null) {
            e.setOtherPaymentMode(otherPaymentMode);
        }
        CollectionCenterEntryDto patchDto = CollectionCenterEntryDto.builder()
                .amountStatus(amountStatus)
                .receivedAmount(receivedAmount)
                .build();
        calculateAndValidateAmounts(e, patchDto);
        auditUtil.setUpdatedBy(e);
        e = repository.save(e);
        return toDto(e);
    }

    @Transactional
    public void delete(String id) {
        CollectionCenterEntry e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection center entry not found: " + id));
        collectionCustomerAwbService.releaseByEntryId(id);
        repository.delete(e);
    }

    private void validateAwb(CollectionCenterEntry e, CollectionCenterEntryDto dto, String excludeId, String priorNorm) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(e.getAwbNo());
        e.setAwbNo(norm);
        if (norm == null) {
            return;
        }
        CourierTrackingNumberValidator.validateFormat(norm);
        if (excludeId != null && Objects.equals(norm, priorNorm)) {
            return;
        }
        awbCenterService.assertAwbAllowedForModule(norm, AwbCenterService.MODULE_COLLECTION_CENTER,
                e.getCollectionCustomerId());
        globalAwbUniquenessService.assertAwbAvailableForCollectionCenter(norm, excludeId, dto.getConsumeRegistryAwbId());
    }

    private void linkRegistryAfterSave(CollectionCenterEntry e, CollectionCenterEntryDto dto) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(e.getAwbNo());
        if (norm == null) {
            return;
        }
        if (StringUtils.hasText(dto.getConsumeRegistryAwbId())) {
            collectionCustomerAwbService.requirePendingForCustomer(dto.getConsumeRegistryAwbId(),
                    e.getCollectionCustomerId(), norm);
            collectionCustomerAwbService.markUsed(dto.getConsumeRegistryAwbId(), e.getId());
        } else {
            collectionCustomerAwbService.createUsedWithoutPending(e.getCollectionCustomerId(), norm, e.getId());
        }
    }

    private void calculateAndValidateAmounts(CollectionCenterEntry e, CollectionCenterEntryDto dto) {
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

    private CollectionCenterEntry toEntity(CollectionCenterEntryDto dto) {
        validatePhoneNumbers(dto.getFromPhoneNumbers(), "From Phone Number");
        validatePhoneNumbers(dto.getToPhoneNumbers(), "To Phone Number");

        CollectionCenterEntry e = CollectionCenterEntry.builder()
                .collectionCustomerId(dto.getCollectionCustomerId())
                .customerName(dto.getCustomerName())
                .entryDate(dto.getEntryDate())
                .entryMonth(dto.getEntryMonth())
                .entryYear(dto.getEntryYear())
                .consignor(dto.getConsignor())
                .receiverName(dto.getReceiverName())
                .pincode(dto.getPincode())
                .state(dto.getState())
                .city(dto.getCity())
                .areaName(dto.getAreaName())
                .courier(dto.getCourier())
                .weight(dto.getWeight())
                .awbNo(dto.getAwbNo())
                .item(dto.getItem())
                .status(dto.getStatus())
                .amount(dto.getAmount())
                .amountStatus(dto.getAmountStatus())
                .paymentMode(dto.getPaymentMode())
                .otherPaymentMode(dto.getOtherPaymentMode())
                .remarks(dto.getRemarks())
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

    private void applyDto(CollectionCenterEntry e, CollectionCenterEntryDto dto) {
        validatePhoneNumbers(dto.getFromPhoneNumbers(), "From Phone Number");
        validatePhoneNumbers(dto.getToPhoneNumbers(), "To Phone Number");

        e.setEntryDate(dto.getEntryDate());
        e.setEntryMonth(dto.getEntryMonth());
        e.setEntryYear(dto.getEntryYear());
        e.setConsignor(dto.getConsignor());
        e.setReceiverName(dto.getReceiverName());
        e.setPincode(dto.getPincode());
        e.setState(dto.getState());
        e.setCity(dto.getCity());
        e.setAreaName(dto.getAreaName());
        e.setCourier(dto.getCourier());
        e.setWeight(dto.getWeight());
        e.setAwbNo(dto.getAwbNo());
        e.setItem(dto.getItem());
        e.setStatus(dto.getStatus());
        e.setAmount(dto.getAmount());
        if (dto.getAmountStatus() != null) {
            e.setAmountStatus(dto.getAmountStatus());
        }
        if (dto.getPaymentMode() != null) {
            e.setPaymentMode(dto.getPaymentMode());
        }
        if (dto.getOtherPaymentMode() != null) {
            e.setOtherPaymentMode(dto.getOtherPaymentMode());
        }
        e.setRemarks(dto.getRemarks());

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

    private CollectionCenterEntryDto toDto(CollectionCenterEntry e) {
        double originalAmount = e.getAmount() != null ? e.getAmount() : 0.0;
        originalAmount = Math.round(originalAmount * 100.0) / 100.0;
        String st = e.getAmountStatus();
        boolean isPaidStatus = st != null && Set.of("PAID", "CASH", "GPAY", "COD").contains(st.trim().toUpperCase());
        Double received = e.getReceivedAmount();
        if (received != null) {
            received = Math.round(received * 100.0) / 100.0;
        } else if (isPaidStatus) {
            received = originalAmount;
        } else {
            received = 0.0;
        }
        Double pending;
        if (isPaidStatus) {
            pending = 0.0;
        } else if (e.getPendingAmount() != null) {
            pending = Math.round(e.getPendingAmount() * 100.0) / 100.0;
        } else {
            pending = Math.round(Math.max(0.0, originalAmount - received) * 100.0) / 100.0;
        }

        return CollectionCenterEntryDto.builder()
                .id(e.getId())
                .collectionCustomerId(e.getCollectionCustomerId())
                .customerName(e.getCustomerName())
                .entryDate(e.getEntryDate())
                .entryMonth(e.getEntryMonth())
                .entryYear(e.getEntryYear())
                .consignor(e.getConsignor())
                .receiverName(e.getReceiverName())
                .pincode(e.getPincode())
                .state(e.getState())
                .city(e.getCity())
                .areaName(e.getAreaName())
                .courier(e.getCourier())
                .weight(e.getWeight())
                .awbNo(e.getAwbNo())
                .item(e.getItem())
                .status(e.getStatus())
                .amount(originalAmount)
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
}

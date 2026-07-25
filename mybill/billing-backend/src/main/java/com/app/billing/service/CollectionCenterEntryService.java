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
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        Query q = buildSearchQuery(collectionCustomerId, customerNameContains, awbNo, dateFrom, dateTo,
                status, amountStatus, pincode, courier, entryMonth, entryYear);
        long total = mongoTemplate.count(q, CollectionCenterEntry.class);
        q.with(resolveCollectionEntrySort(sortBy, sortDir));
        q.skip((long) page * size).limit(size);
        List<CollectionCenterEntryDto> rows = mongoTemplate.find(q, CollectionCenterEntry.class).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PaginationUtil.toPageResponse(rows, page, size, (int) total);
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
            String pincode,
            String courier,
            String entryMonth,
            Integer entryYear) {
        Query q = buildSearchQuery(collectionCustomerId, customerNameContains, awbNo, dateFrom, dateTo,
                status, amountStatus, pincode, courier, entryMonth, entryYear);
        List<CollectionCenterEntry> all = mongoTemplate.find(q, CollectionCenterEntry.class);
        double sum = all.stream().mapToDouble(en -> en.getAmount() != null ? en.getAmount() : 0d).sum();
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
    public CollectionCenterEntryDto patchStatus(String id, String amountStatus, String remarks) {
        CollectionCenterEntry e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection center entry not found: " + id));
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

    private CollectionCenterEntry toEntity(CollectionCenterEntryDto dto) {
        return CollectionCenterEntry.builder()
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
                .remarks(dto.getRemarks())
                .build();
    }

    private void applyDto(CollectionCenterEntry e, CollectionCenterEntryDto dto) {
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
        e.setAmountStatus(dto.getAmountStatus());
        e.setRemarks(dto.getRemarks());
    }

    private CollectionCenterEntryDto toDto(CollectionCenterEntry e) {
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
                .amount(e.getAmount())
                .amountStatus(e.getAmountStatus())
                .remarks(e.getRemarks())
                .lastUpdatedBy(e.getLastUpdatedBy())
                .build();
    }
}

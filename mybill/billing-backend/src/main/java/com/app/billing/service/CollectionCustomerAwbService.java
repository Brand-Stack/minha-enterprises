package com.app.billing.service;

import com.app.billing.dao.CollectionCustomerAwbRepository;
import com.app.billing.dao.CollectionCenterEntryRepository;
import com.app.billing.dao.CollectionCustomerRepository;
import com.app.billing.dto.AwbBatchValidationResultDto;
import com.app.billing.dto.AwbConflictLineDto;
import com.app.billing.dto.BulkAwbRegisterRequestDto;
import com.app.billing.dto.BulkAwbRegisterResultDto;
import com.app.billing.dto.CollectionCustomerAwbDto;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.CollectionCustomer;
import com.app.billing.model.CollectionCustomerAwb;
import com.app.billing.util.CourierTrackingNumberValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionCustomerAwbService {

    private final CollectionCustomerAwbRepository awbRepository;
    private final CollectionCenterEntryRepository collectionCenterEntryRepository;
    private final CollectionCustomerRepository customerRepository;
    private final GlobalAwbUniquenessService globalAwbUniquenessService;
    private final MongoTemplate mongoTemplate;
    private final AwbCenterService awbCenterService;

    /**
     * Registers as many AWBs as possible. Each successful insert commits independently (partial success).
     * Duplicates in the request, invalid format, conflicts, and rows already on this customer are reported separately.
     */
    public BulkAwbRegisterResultDto registerBulkWithReport(String collectionCustomerId, BulkAwbRegisterRequestDto body) {
        CollectionCustomer parent = customerRepository.findById(collectionCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
        String customerName = parent.getCustomerName() != null ? parent.getCustomerName() : "";
        List<CollectionCustomerAwbDto> created = new ArrayList<>();
        List<String> duplicateInRequest = new ArrayList<>();
        List<String> invalidFormat = new ArrayList<>();
        List<String> alreadyOnCustomer = new ArrayList<>();
        List<AwbConflictLineDto> conflicts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : body.getAwbNumbers()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            String norm;
            try {
                norm = CourierTrackingNumberValidator.normalizeOrNull(trimmed);
                if (norm == null) {
                    invalidFormat.add(trimmed);
                    continue;
                }
                CourierTrackingNumberValidator.validateFormat(norm);
            } catch (IllegalArgumentException e) {
                invalidFormat.add(trimmed);
                continue;
            }
            if (!seen.add(norm)) {
                if (!duplicateInRequest.contains(norm)) {
                    duplicateInRequest.add(norm);
                }
                continue;
            }
            Optional<CollectionCustomerAwb> existingSame = awbRepository.findFirstByAwbNoIgnoreCase(norm);
            if (existingSame.isPresent() && collectionCustomerId.equals(existingSame.get().getCollectionCustomerId())) {
                alreadyOnCustomer.add(norm);
                continue;
            }
            if (existingSame.isPresent()) {
                conflicts.add(AwbConflictLineDto.builder()
                        .awbNo(norm)
                        .message("AWB is already registered under another collection customer.")
                        .build());
                continue;
            }
            try {
                globalAwbUniquenessService.assertAwbFreeForNewRegistry(norm);
            } catch (ResourceAlreadyExistsException ex) {
                conflicts.add(AwbConflictLineDto.builder().awbNo(norm).message(ex.getMessage()).build());
                continue;
            }
            CollectionCustomerAwb row = CollectionCustomerAwb.builder()
                    .collectionCustomerId(collectionCustomerId)
                    .awbNo(norm)
                    .status(CollectionCustomerAwb.STATUS_PENDING)
                    .build();
            row = awbRepository.save(row);
            created.add(toDto(row, customerName));
        }
        return BulkAwbRegisterResultDto.builder()
                .created(created)
                .duplicateInRequest(duplicateInRequest)
                .invalidFormat(invalidFormat)
                .alreadyOnCustomer(alreadyOnCustomer)
                .conflicts(conflicts)
                .build();
    }

    /**
     * Removes a PENDING registry row for this customer. USED rows cannot be deleted (linked to collection entries).
     */
    @Transactional
    public void deletePendingRegistryRow(String collectionCustomerId, String registryId) {
        CollectionCustomerAwb r = awbRepository.findById(registryId)
                .orElseThrow(() -> new ResourceNotFoundException("AWB registry row not found"));
        if (!collectionCustomerId.equals(r.getCollectionCustomerId())) {
            throw new IllegalArgumentException("AWB does not belong to this collection customer.");
        }
        if (!CollectionCustomerAwb.STATUS_PENDING.equalsIgnoreCase(String.valueOf(r.getStatus()))) {
            throw new IllegalArgumentException(
                    "Only pending AWBs can be removed. This AWB is already used on a collection entry.");
        }
        awbRepository.delete(r);
    }

    /**
     * Validates AWB tokens from the UI: duplicates in the same request, format, global uniqueness
     * (monthly / collection entries / registry). When {@code collectionCustomerId} is set, an AWB row
     * already on that customer's registry is allowed.
     */
    public AwbBatchValidationResultDto validateBatch(String collectionCustomerId, List<String> rawTokens) {
        List<String> invalidFormat = new ArrayList<>();
        List<String> duplicateInRequest = new ArrayList<>();
        List<AwbConflictLineDto> conflicts = new ArrayList<>();
        if (rawTokens == null || rawTokens.isEmpty()) {
            return AwbBatchValidationResultDto.builder().valid(true).build();
        }
        List<String> normalizedSequence = new ArrayList<>();
        for (String raw : rawTokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String t = raw.trim();
            try {
                CourierTrackingNumberValidator.validateFormat(t);
            } catch (IllegalArgumentException e) {
                invalidFormat.add(t);
                continue;
            }
            String norm = CourierTrackingNumberValidator.normalizeOrNull(t);
            if (norm == null || norm.isEmpty()) {
                invalidFormat.add(t);
                continue;
            }
            normalizedSequence.add(norm.toUpperCase(Locale.ROOT));
        }
        Map<String, Long> freq = normalizedSequence.stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        for (Map.Entry<String, Long> e : freq.entrySet()) {
            if (e.getValue() > 1L) {
                duplicateInRequest.add(e.getKey());
            }
        }
        LinkedHashSet<String> uniqueOrder = new LinkedHashSet<>(normalizedSequence);
        for (String norm : uniqueOrder) {
            if (freq.get(norm) > 1L) {
                continue;
            }
            Optional<CollectionCustomerAwb> reg = awbRepository.findFirstByAwbNoIgnoreCase(norm);
            if (reg.isPresent()) {
                if (StringUtils.hasText(collectionCustomerId)
                        && collectionCustomerId.equals(reg.get().getCollectionCustomerId())) {
                    continue;
                }
                conflicts.add(AwbConflictLineDto.builder()
                        .awbNo(norm)
                        .message("AWB is already registered under another collection customer.")
                        .build());
                continue;
            }
            try {
                globalAwbUniquenessService.assertAwbFreeForNewRegistry(norm);
            } catch (ResourceAlreadyExistsException ex) {
                conflicts.add(AwbConflictLineDto.builder().awbNo(norm).message(ex.getMessage()).build());
            }
        }
        boolean valid = duplicateInRequest.isEmpty() && invalidFormat.isEmpty() && conflicts.isEmpty();
        return AwbBatchValidationResultDto.builder()
                .valid(valid)
                .duplicateInRequest(duplicateInRequest)
                .invalidFormat(invalidFormat)
                .conflicts(conflicts)
                .build();
    }

    public List<CollectionCustomerAwbDto> listForCustomer(String collectionCustomerId) {
        CollectionCustomer parent = customerRepository.findById(collectionCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
        String name = parent.getCustomerName() != null ? parent.getCustomerName() : "";
        return awbRepository.findByCollectionCustomerIdOrderByCreatedAtDesc(collectionCustomerId).stream()
                .map(r -> toDto(r, name))
                .collect(Collectors.toList());
    }

    public List<CollectionCustomerAwbDto> listPendingForCustomer(String collectionCustomerId) {
        CollectionCustomer parent = customerRepository.findById(collectionCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
        String name = parent.getCustomerName() != null ? parent.getCustomerName() : "";
        return awbRepository.findByCollectionCustomerIdAndStatusOrderByCreatedAtDesc(collectionCustomerId,
                        CollectionCustomerAwb.STATUS_PENDING).stream()
                .map(r -> toDto(r, name))
                .collect(Collectors.toList());
    }

    /**
     * Pending AWBs across customers, with optional filters (createdAt month/year when entryMonth/Year not on registry —
     * we filter by {@code createdAt} of the registry row).
     */
    public List<CollectionCustomerAwbDto> listPendingOverall(
            String collectionCustomerId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Integer calendarMonth,
            Integer calendarYear) {
        Query q = new Query(Criteria.where("status").is(CollectionCustomerAwb.STATUS_PENDING));
        if (StringUtils.hasText(collectionCustomerId)) {
            q.addCriteria(Criteria.where("collectionCustomerId").is(collectionCustomerId.trim()));
        }
        if (createdFrom != null) {
            q.addCriteria(Criteria.where("createdAt").gte(createdFrom));
        }
        if (createdTo != null) {
            q.addCriteria(Criteria.where("createdAt").lte(createdTo));
        }
        if (calendarYear != null) {
            LocalDateTime start;
            LocalDateTime end;
            if (calendarMonth != null && calendarMonth >= 1 && calendarMonth <= 12) {
                start = LocalDateTime.of(calendarYear, Month.of(calendarMonth), 1, 0, 0);
                end = start.plusMonths(1).minusNanos(1);
            } else {
                start = LocalDateTime.of(calendarYear, Month.JANUARY, 1, 0, 0);
                end = LocalDateTime.of(calendarYear, Month.DECEMBER, 31, 23, 59, 59);
            }
            q.addCriteria(Criteria.where("createdAt").gte(start).lte(end));
        }
        q.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        List<CollectionCustomerAwb> rows = mongoTemplate.find(q, CollectionCustomerAwb.class);
        return rows.stream().map(r -> {
            String nm = customerRepository.findById(r.getCollectionCustomerId())
                    .map(CollectionCustomer::getCustomerName).orElse("");
            return toDto(r, nm);
        }).collect(Collectors.toList());
    }

    public long countPendingForCustomer(String collectionCustomerId) {
        return awbRepository.countByCollectionCustomerIdAndStatus(collectionCustomerId, CollectionCustomerAwb.STATUS_PENDING);
    }

    /**
     * Removes only PENDING AWBs for the given customer, preserving USED rows.
     *
     * @return number of rows deleted
     */
    @Transactional
    public long clearPendingForCustomer(String collectionCustomerId) {
        List<CollectionCustomerAwb> pendingRows = resolvePendingRowsByEntryUsage(collectionCustomerId);
        if (pendingRows.isEmpty()) {
            return 0;
        }
        Set<String> awbKeys = pendingRows.stream()
                .map(r -> CourierTrackingNumberValidator.normalizeOrNull(r.getAwbNo()))
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());
        awbRepository.deleteAll(pendingRows);
        awbCenterService.bulkDeletePendingCenterByAwbNumbers(awbKeys);
        return pendingRows.size();
    }

    /**
     * Clears all pending registry AWBs across all collection customers.
     * Used AWBs are preserved (identified by Collection Center entry usage).
     */
    @Transactional
    public long clearPendingAcrossAllCustomers() {
        List<CollectionCustomerAwb> allRows = awbRepository.findAll();
        if (allRows.isEmpty()) {
            return 0;
        }
        Set<String> usedKeys = collectionCenterEntryRepository.findAll().stream()
                .filter(e -> StringUtils.hasText(e.getCollectionCustomerId()))
                .map(e -> compositeKey(e.getCollectionCustomerId(), e.getAwbNo()))
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());
        List<CollectionCustomerAwb> pendingRows = allRows.stream()
                .filter(r -> StringUtils.hasText(r.getCollectionCustomerId()))
                .filter(r -> !CollectionCustomerAwb.STATUS_USED.equalsIgnoreCase(String.valueOf(r.getStatus())))
                .filter(r -> {
                    String key = compositeKey(r.getCollectionCustomerId(), r.getAwbNo());
                    return key != null && !key.isBlank() && !usedKeys.contains(key);
                })
                .collect(Collectors.toList());
        if (pendingRows.isEmpty()) {
            return 0;
        }
        Set<String> awbKeys = pendingRows.stream()
                .map(r -> CourierTrackingNumberValidator.normalizeOrNull(r.getAwbNo()))
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());
        awbRepository.deleteAll(pendingRows);
        awbCenterService.bulkDeletePendingCenterByAwbNumbers(awbKeys);
        awbCenterService.deletePendingCollectionAwbsFromCenter();
        return pendingRows.size();
    }

    /**
     * Preview counts and first N pending AWBs for the selected customer.
     * "Pending" means AWB is not used in any collection-center entry for this customer.
     */
    public Map<String, Object> previewPendingForCustomer(String collectionCustomerId, int previewLimit) {
        CollectionCustomer parent = customerRepository.findById(collectionCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
        List<CollectionCustomerAwb> allRows = awbRepository.findByCollectionCustomerIdOrderByCreatedAtDesc(collectionCustomerId);
        Set<String> usedAwbKeys = usedAwbKeysForCustomer(collectionCustomerId);
        List<CollectionCustomerAwb> pendingRows = allRows.stream()
                .filter(r -> isPendingByEntryUsage(r, usedAwbKeys))
                .collect(Collectors.toList());

        int safePreviewLimit = Math.max(previewLimit, 0);
        List<Map<String, Object>> preview = pendingRows.stream()
                .limit(safePreviewLimit)
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r.getId());
                    row.put("awbNo", r.getAwbNo());
                    row.put("status", "PENDING");
                    row.put("createdAt", r.getCreatedAt());
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("collectionCustomerId", collectionCustomerId);
        out.put("customerName", parent.getCustomerName() != null ? parent.getCustomerName() : "");
        out.put("totalAwbs", allRows.size());
        out.put("pendingAwbs", pendingRows.size());
        out.put("pendingPreview", preview);
        return out;
    }

    private List<CollectionCustomerAwb> resolvePendingRowsByEntryUsage(String collectionCustomerId) {
        customerRepository.findById(collectionCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection customer not found"));
        List<CollectionCustomerAwb> allRows = awbRepository.findByCollectionCustomerIdOrderByCreatedAtDesc(collectionCustomerId);
        if (allRows.isEmpty()) {
            return List.of();
        }
        Set<String> usedAwbKeys = usedAwbKeysForCustomer(collectionCustomerId);
        return allRows.stream()
                .filter(r -> isPendingByEntryUsage(r, usedAwbKeys))
                .collect(Collectors.toList());
    }

    private Set<String> usedAwbKeysForCustomer(String collectionCustomerId) {
        return collectionCenterEntryRepository.findByCollectionCustomerIdOrderByEntryDateDesc(collectionCustomerId).stream()
                .map(e -> normalizeAwbKey(e.getAwbNo()))
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean isPendingByEntryUsage(CollectionCustomerAwb row, Set<String> usedAwbKeys) {
        if (row == null) {
            return false;
        }
        if (CollectionCustomerAwb.STATUS_USED.equalsIgnoreCase(String.valueOf(row.getStatus()))) {
            return false;
        }
        String k = normalizeAwbKey(row.getAwbNo());
        return k != null && !k.isBlank() && !usedAwbKeys.contains(k);
    }

    private String normalizeAwbKey(String awbNo) {
        return awbNo != null ? awbNo.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String compositeKey(String customerId, String awbNo) {
        if (!StringUtils.hasText(customerId)) {
            return null;
        }
        String awbKey = normalizeAwbKey(awbNo);
        if (!StringUtils.hasText(awbKey)) {
            return null;
        }
        return customerId.trim() + "|" + awbKey;
    }

    CollectionCustomerAwb requirePendingForCustomer(String registryId, String collectionCustomerId, String expectedAwbNorm) {
        CollectionCustomerAwb r = awbRepository.findById(registryId)
                .orElseThrow(() -> new IllegalArgumentException("AWB registry row not found."));
        if (!collectionCustomerId.equals(r.getCollectionCustomerId())) {
            throw new IllegalArgumentException("AWB does not belong to the selected collection customer.");
        }
        if (!CollectionCustomerAwb.STATUS_PENDING.equalsIgnoreCase(String.valueOf(r.getStatus()))) {
            throw new IllegalArgumentException("AWB is not pending in the registry.");
        }
        if (expectedAwbNorm != null && !expectedAwbNorm.equalsIgnoreCase(r.getAwbNo())) {
            throw new IllegalArgumentException("AWB does not match the selected registry row.");
        }
        return r;
    }

    @Transactional
    public void markUsed(String registryId, String entryId) {
        CollectionCustomerAwb r = awbRepository.findById(registryId)
                .orElseThrow(() -> new ResourceNotFoundException("Registry AWB not found"));
        r.setStatus(CollectionCustomerAwb.STATUS_USED);
        r.setCollectionCenterEntryId(entryId);
        awbRepository.save(r);
    }

    @Transactional
    public void markPendingAndClearEntry(String registryId) {
        awbRepository.findById(registryId).ifPresent(r -> {
            r.setStatus(CollectionCustomerAwb.STATUS_PENDING);
            r.setCollectionCenterEntryId(null);
            awbRepository.save(r);
        });
    }

    @Transactional
    public void releaseByEntryId(String collectionCenterEntryId) {
        awbRepository.findByCollectionCenterEntryId(collectionCenterEntryId).ifPresent(r -> {
            r.setStatus(CollectionCustomerAwb.STATUS_PENDING);
            r.setCollectionCenterEntryId(null);
            awbRepository.save(r);
        });
    }

    @Transactional
    public CollectionCustomerAwb createUsedWithoutPending(String collectionCustomerId, String normAwb, String entryId) {
        CollectionCustomerAwb row = CollectionCustomerAwb.builder()
                .collectionCustomerId(collectionCustomerId)
                .awbNo(normAwb)
                .status(CollectionCustomerAwb.STATUS_USED)
                .collectionCenterEntryId(entryId)
                .build();
        return awbRepository.save(row);
    }

    private static CollectionCustomerAwbDto toDto(CollectionCustomerAwb r, String customerName) {
        return CollectionCustomerAwbDto.builder()
                .id(r.getId())
                .collectionCustomerId(r.getCollectionCustomerId())
                .customerName(customerName)
                .awbNo(r.getAwbNo())
                .status(r.getStatus())
                .collectionCenterEntryId(r.getCollectionCenterEntryId())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

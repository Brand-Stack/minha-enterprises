package com.app.billing.service;
import com.app.billing.dao.AwbCenterRepository;
import com.app.billing.dao.CashBookingRepository;
import com.app.billing.dao.CollectionCenterEntryRepository;
import com.app.billing.dao.CollectionCustomerAwbRepository;
import com.app.billing.dao.CollectionCustomerRepository;
import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.model.CollectionCustomerAwb;
import com.app.billing.model.CollectionCustomer;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.dto.AwbCenterBulkGenerateRequest;
import com.app.billing.dto.AwbCollectionSummaryDto;
import com.app.billing.dto.AwbCourierTypeStatsDto;
import com.app.billing.dto.AwbCenterQueueStatsDto;
import com.app.billing.dto.AwbCenterRecordDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AwbCenterRecord;
import com.app.billing.model.CashBooking;
import com.app.billing.model.CollectionCenterEntry;
import com.app.billing.model.MonthlyCourierEntry;
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

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AwbCenterService {

    public static final String AWB_NOT_IN_CENTER_MSG = "AWB is not available in AWB Center";
    public static final String AWB_NOT_FOR_COLLECTION_MSG = "This AWB is not configured for Collection Customers";
    public static final String AWB_RESERVED_FOR_COLLECTION_MSG = "This AWB is reserved for Collection Customer usage";

    public static final String MODULE_COLLECTION_CENTER = "COLLECTION_CENTER";
    public static final String MODULE_CLIENT_ENTRY = "CLIENT_ENTRY";
    public static final String MODULE_CASH_BOOKING = "CASH_BOOKING";
    public static final String MODULE_SMALL_CLIENT_ENTRY = "SMALL_CLIENT_ENTRY";

    private final AwbCenterRepository repository;
    private final MongoTemplate mongoTemplate;
    private final CompanySettingsService companySettingsService;
    private final AuditUtil auditUtil;
    private final MonthlyCourierEntryRepository monthlyCourierEntryRepository;
    private final CollectionCenterEntryRepository collectionCenterEntryRepository;
    private final CashBookingRepository cashBookingRepository;
    private final SmallClientEntryRepository smallClientEntryRepository;
    private final CollectionCustomerAwbRepository collectionCustomerAwbRepository;
    private final CollectionCustomerRepository customerRepository;

    public PageResponse<AwbCenterRecordDto> search(
            String awbNumber,
            String queueStatus,
            String awbType,
            String courierType,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean unmapped,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        Query q = buildSearchQuery(awbNumber, queueStatus, awbType, courierType, dateFrom, dateTo, unmapped);
        long total = mongoTemplate.count(q, AwbCenterRecord.class);
        String field = StringUtils.hasText(sortBy) ? sortBy : "awbNumber";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (!SAFE_SORT_FIELDS.contains(field)) {
            field = "awbNumber";
        }
        List<AwbCenterRecord> records;
        if ("awbNumber".equals(field)) {
            records = mongoTemplate.find(q, AwbCenterRecord.class);
            Comparator<AwbCenterRecord> cmp = Comparator.comparing(
                    r -> parseAwbNumeric(r.getAwbNumber()), Comparator.nullsLast(Comparator.naturalOrder()));
            if (dir == Sort.Direction.DESC) {
                cmp = cmp.reversed();
            }
            records.sort(cmp);
            int from = page * size;
            if (from >= records.size()) {
                records = List.of();
            } else {
                int to = Math.min(from + size, records.size());
                records = records.subList(from, to);
            }
        } else {
            q.with(Sort.by(dir, field));
            q.skip((long) page * size).limit(size);
            records = mongoTemplate.find(q, AwbCenterRecord.class);
        }
        List<AwbCenterRecordDto> content = records.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PaginationUtil.toPageResponse(content, page, size, total);
    }

    private static BigInteger parseAwbNumeric(String awb) {
        if (awb == null || awb.isBlank()) {
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(awb.trim());
        } catch (NumberFormatException ex) {
            return BigInteger.ZERO;
        }
    }

    private static final java.util.Set<String> SAFE_SORT_FIELDS = java.util.Set.of(
            "createdAt", "awbNumber", "queueStatus", "completedAt", "courierType");

    private Query buildSearchQuery(String awbNumber, String queueStatus, String awbType, String courierType,
            LocalDate dateFrom, LocalDate dateTo, Boolean unmapped) {
        Query q = new Query();
        if (StringUtils.hasText(awbNumber)) {
            q.addCriteria(Criteria.where("awbNumber").regex(".*" + Pattern.quote(awbNumber.trim()) + ".*", "i"));
        }
        if (StringUtils.hasText(queueStatus)) {
            q.addCriteria(Criteria.where("queueStatus").is(queueStatus.trim().toUpperCase(Locale.ROOT)));
        }
        applyAwbTypeCriteria(q, awbType);
        if (StringUtils.hasText(courierType)) {
            q.addCriteria(Criteria.where("courierType").is(courierType.trim()));
        }
        if (Boolean.TRUE.equals(unmapped)) {
            q.addCriteria(Criteria.where("collectionCustomerAwb").is(true));
            List<String> mappedAwbNos = collectionCustomerAwbRepository.findAll().stream()
                    .map(CollectionCustomerAwb::getAwbNo)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toList());
            q.addCriteria(Criteria.where("awbNumber").nin(mappedAwbNos));
        }
        if (dateFrom != null || dateTo != null) {
            Criteria created = Criteria.where("createdAt");
            if (dateFrom != null) {
                created = created.gte(dateFrom.atStartOfDay());
            }
            if (dateTo != null) {
                created = created.lte(dateTo.atTime(23, 59, 59));
            }
            q.addCriteria(created);
        }
        return q;
    }

    public AwbCenterQueueStatsDto queueStats() {
        return queueStats(null);
    }

    public AwbCenterQueueStatsDto queueStats(String courierType) {
        List<String> configuredCouriers = companySettingsService.getSettings().getCouriers();
        java.util.LinkedHashSet<String> courierNames = new java.util.LinkedHashSet<>();
        if (configuredCouriers != null) {
            configuredCouriers.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(courierNames::add);
        }
        mongoTemplate.findDistinct(new Query(), "courierType", AwbCenterRecord.class, String.class)
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(courierNames::add);

        List<AwbCourierTypeStatsDto> courierStats = new ArrayList<>();
        long pending = 0;
        long completed = 0;
        for (String name : courierNames) {
            long cPending = countCourierType(name, AwbCenterRecord.QUEUE_PENDING);
            long cCompleted = countCourierType(name, AwbCenterRecord.QUEUE_COMPLETED);
            courierStats.add(AwbCourierTypeStatsDto.builder()
                    .courierType(name)
                    .pendingCount(cPending)
                    .completedCount(cCompleted)
                    .totalCount(cPending + cCompleted)
                    .build());
            pending += cPending;
            completed += cCompleted;
        }

        long collPending = countByStatusAndType(AwbCenterRecord.QUEUE_PENDING, true, null);
        long collCompleted = countByStatusAndType(AwbCenterRecord.QUEUE_COMPLETED, true, null);
        long collTotal = collPending + collCompleted;
        java.util.Set<String> registeredAwbs = collectionCustomerAwbRepository.findAll().stream()
                .map(CollectionCustomerAwb::getAwbNo)
                .filter(StringUtils::hasText)
                .map(a -> a.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Query collectionQuery = new Query();
        applyAwbTypeCriteria(collectionQuery, "COLLECTION");
        List<AwbCenterRecord> collectionRows = mongoTemplate.find(collectionQuery, AwbCenterRecord.class);
        long collectionMapped = collectionRows.stream()
                .filter(r -> r.getAwbNumber() != null
                        && registeredAwbs.contains(r.getAwbNumber().trim().toUpperCase(Locale.ROOT)))
                .count();
        long collectionUnmapped = Math.max(0, collTotal - collectionMapped);

        AwbCollectionSummaryDto collectionSummary = AwbCollectionSummaryDto.builder()
                .totalCount(collTotal)
                .mappedCount(collectionMapped)
                .unmappedCount(collectionUnmapped)
                .pendingCount(collPending)
                .completedCount(collCompleted)
                .build();

        long stdPending = countByStatusAndType(AwbCenterRecord.QUEUE_PENDING, false, null);
        long stdCompleted = countByStatusAndType(AwbCenterRecord.QUEUE_COMPLETED, false, null);

        java.util.Map<String, Long> legacyCourierCounts = new java.util.LinkedHashMap<>();
        for (AwbCourierTypeStatsDto s : courierStats) {
            legacyCourierCounts.put(s.getCourierType(), s.getTotalCount());
        }

        return AwbCenterQueueStatsDto.builder()
                .pendingCount(pending)
                .completedCount(completed)
                .totalCount(pending + completed)
                .courierTypeStats(courierStats)
                .collectionSummary(collectionSummary)
                .courierTypeCounts(legacyCourierCounts)
                .standardPendingCount(stdPending)
                .standardCompletedCount(stdCompleted)
                .standardTotalCount(stdPending + stdCompleted)
                .collectionPendingCount(collPending)
                .collectionCompletedCount(collCompleted)
                .collectionTotalCount(collTotal)
                .collectionMappedCount(collectionMapped)
                .collectionUnmappedCount(collectionUnmapped)
                .build();
    }

    private long countCourierType(String courierType, String queueStatus) {
        Query q = Query.query(Criteria.where("courierType").is(courierType.trim())
                .and("queueStatus").is(queueStatus));
        return mongoTemplate.count(q, AwbCenterRecord.class);
    }

    private long countByStatusAndType(String queueStatus, boolean collection, String courierType) {
        Query q = Query.query(Criteria.where("queueStatus").is(queueStatus));
        applyAwbTypeCriteria(q, collection ? "COLLECTION" : "STANDARD");
        if (StringUtils.hasText(courierType)) {
            q.addCriteria(Criteria.where("courierType").is(courierType.trim()));
        }
        return mongoTemplate.count(q, AwbCenterRecord.class);
    }

    private long countWithOptionalCourier(Criteria base, String courierType) {
        Query q = Query.query(base);
        if (StringUtils.hasText(courierType)) {
            q.addCriteria(Criteria.where("courierType").is(courierType.trim()));
        }
        return mongoTemplate.count(q, AwbCenterRecord.class);
    }

    private long countByStatusAndType(String queueStatus, boolean collection) {
        return countByStatusAndType(queueStatus, collection, null);
    }

    private void applyAwbTypeCriteria(Query q, String awbType) {
        if (!StringUtils.hasText(awbType)) {
            return;
        }
        String t = awbType.trim().toUpperCase(Locale.ROOT);
        if ("COLLECTION".equals(t)) {
            q.addCriteria(Criteria.where("collectionCustomerAwb").is(true));
        } else if ("STANDARD".equals(t)) {
            q.addCriteria(new Criteria().orOperator(
                    Criteria.where("collectionCustomerAwb").is(false),
                    Criteria.where("collectionCustomerAwb").exists(false)));
        }
    }

    public AwbCenterRecordDto findById(String id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("AWB record not found: " + id));
    }

    @Transactional
    public AwbCenterRecordDto createSingle(String rawAwb, Boolean collectionCustomerAwb,
            String collectionCustomerId, String collectionCustomerName, String courierType) {
        String norm = normalizeRequired(rawAwb);
        if (repository.existsByAwbNumberIgnoreCase(norm)) {
            throw new ResourceAlreadyExistsException("AWB already exists in AWB Center");
        }
        boolean collection = Boolean.TRUE.equals(collectionCustomerAwb);
        AwbCenterRecord row = AwbCenterRecord.builder()
                .awbNumber(norm)
                .queueStatus(AwbCenterRecord.QUEUE_PENDING)
                .collectionCustomerAwb(collection)
                .collectionCustomerId(null)
                .collectionCustomerName(null)
                .courierType(StringUtils.hasText(courierType) ? courierType.trim() : null)
                .build();
        auditUtil.setCreatedBy(row);
        row = repository.save(row);
        syncUsageFromModules(norm);
        return toDto(repository.findById(row.getId()).orElse(row));
    }

    @Transactional
    public AwbCenterRecordDto update(String id, String rawAwb) {
        AwbCenterRecord row = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AWB record not found: " + id));
        String norm = normalizeRequired(rawAwb);
        if (!norm.equalsIgnoreCase(row.getAwbNumber())
                && repository.existsByAwbNumberIgnoreCase(norm)) {
            throw new ResourceAlreadyExistsException("AWB already exists in AWB Center");
        }
        row.setAwbNumber(norm);
        auditUtil.setUpdatedBy(row);
        row = repository.save(row);
        syncUsageFromModules(norm);
        return toDto(row);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("AWB record not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public int bulkGenerate(AwbCenterBulkGenerateRequest request) {
        boolean collection = Boolean.TRUE.equals(request.getCollectionCustomerAwb());
        String start = normalizeRequired(request.getStartAwb());
        String end = normalizeRequired(request.getEndAwb());
        BigInteger startNum = new BigInteger(start);
        BigInteger endNum = new BigInteger(end);
        if (startNum.compareTo(endNum) > 0) {
            BigInteger tmp = startNum;
            startNum = endNum;
            endNum = tmp;
        }
        int frequency = resolveAwbFrequency();
        BigInteger step = BigInteger.valueOf(frequency);
        List<AwbCenterRecord> batch = new ArrayList<>();
        int created = 0;
        int maxBatch = 500;
        for (BigInteger cur = startNum; cur.compareTo(endNum) <= 0; cur = cur.add(step)) {
            String awb = cur.toString();
            if (repository.existsByAwbNumberIgnoreCase(awb)) {
                continue;
            }
            AwbCenterRecord row = AwbCenterRecord.builder()
                    .awbNumber(awb)
                    .queueStatus(AwbCenterRecord.QUEUE_PENDING)
                    .collectionCustomerAwb(collection)
                    .collectionCustomerId(null)
                    .collectionCustomerName(null)
                    .courierType(StringUtils.hasText(request.getCourierType()) ? request.getCourierType().trim() : null)
                    .build();
            auditUtil.setCreatedBy(row);
            batch.add(row);
            if (batch.size() >= maxBatch) {
                repository.saveAll(batch);
                created += batch.size();
                batch.clear();
            }
            if (created + batch.size() > 5000) {
                throw new IllegalArgumentException("Bulk generation limited to 5000 AWBs per request");
            }
        }
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            created += batch.size();
        }
        for (BigInteger cur = startNum; cur.compareTo(endNum) <= 0; cur = cur.add(step)) {
            syncUsageFromModules(cur.toString());
        }
        return created;
    }

    public void assertExistsInCenter(String rawAwb) {
        assertAwbAllowedForModule(rawAwb, MODULE_COLLECTION_CENTER, null);
    }

    /**
     * Validates AWB exists in center and matches module-specific collection vs non-collection rules.
     */
    public void assertAwbAllowedForModule(String rawAwb, String module, String collectionCustomerId) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(rawAwb);
        if (norm == null || !CourierTrackingNumberValidator.isPurelyNumeric(norm)) {
            return;
        }
        Optional<AwbCenterRecord> opt = repository.findFirstByAwbNumberIgnoreCase(norm);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException(AWB_NOT_IN_CENTER_MSG);
        }
        AwbCenterRecord row = opt.get();
        boolean isCollectionAwb = Boolean.TRUE.equals(row.getCollectionCustomerAwb());
        if (MODULE_COLLECTION_CENTER.equals(module)) {
            if (!isCollectionAwb) {
                throw new IllegalArgumentException(AWB_NOT_FOR_COLLECTION_MSG);
            }
        } else if (MODULE_CLIENT_ENTRY.equals(module) || MODULE_CASH_BOOKING.equals(module)
                || MODULE_SMALL_CLIENT_ENTRY.equals(module)) {
            if (isCollectionAwb) {
                throw new IllegalArgumentException(AWB_RESERVED_FOR_COLLECTION_MSG);
            }
        }
    }

    @Transactional
    public void markCompletedIfPresent(String rawAwb, String module, String referenceId) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(rawAwb);
        if (norm == null) {
            return;
        }
        Optional<AwbCenterRecord> opt = repository.findFirstByAwbNumberIgnoreCase(norm);
        if (opt.isEmpty()) {
            return;
        }
        AwbCenterRecord row = opt.get();
        if (AwbCenterRecord.QUEUE_COMPLETED.equals(row.getQueueStatus())) {
            return;
        }
        row.setQueueStatus(AwbCenterRecord.QUEUE_COMPLETED);
        row.setCompletedAt(LocalDateTime.now());
        row.setUsedInModule(module);
        row.setUsedInReferenceId(referenceId);
        auditUtil.setUpdatedBy(row);
        repository.save(row);
    }

    /** Reconcile queue status from live module usage (runtime sync). */
    @Transactional
    public void syncUsageFromModules(String normAwb) {
        if (normAwb == null || normAwb.isBlank()) {
            return;
        }
        Optional<AwbCenterRecord> opt = repository.findFirstByAwbNumberIgnoreCase(normAwb);
        if (opt.isEmpty()) {
            return;
        }
        AwbCenterRecord row = opt.get();
        String module = null;
        String refId = null;
        Optional<MonthlyCourierEntry> m = monthlyCourierEntryRepository.findFirstByTrackingNumberIgnoreCase(normAwb);
        if (m.isPresent()) {
            module = "CLIENT_ENTRY";
            refId = m.get().getId();
        } else {
            Optional<CollectionCenterEntry> c = collectionCenterEntryRepository.findFirstByAwbNoIgnoreCase(normAwb);
            if (c.isPresent()) {
                module = "COLLECTION_CENTER";
                refId = c.get().getId();
            } else {
                Optional<CashBooking> cb = cashBookingRepository.findFirstByAwbNoIgnoreCase(normAwb);
                if (cb.isPresent()) {
                    module = "CASH_BOOKING";
                    refId = cb.get().getId();
                }
            }
        }
        if (module != null) {
            row.setQueueStatus(AwbCenterRecord.QUEUE_COMPLETED);
            row.setCompletedAt(row.getCompletedAt() != null ? row.getCompletedAt() : LocalDateTime.now());
            row.setUsedInModule(module);
            row.setUsedInReferenceId(refId);
            repository.save(row);
        }
    }

    /**
     * Deletes all pending AWB Center rows that are not referenced in any module, and clears
     * matching pending rows from the collection customer registry.
     */
    private static final int DELETE_CHUNK_SIZE = 500;

    /** Removes pending collection-type AWB Center rows not referenced in any module. */
    @Transactional
    public long deletePendingCollectionAwbsFromCenter() {
        Set<String> usedAwbs = loadUsedAwbKeysNormalized();
        List<AwbCenterRecord> pending = mongoTemplate.find(
                Query.query(Criteria.where("queueStatus").is(AwbCenterRecord.QUEUE_PENDING)
                        .and("collectionCustomerAwb").is(true)),
                AwbCenterRecord.class);
        List<AwbCenterRecord> deletable = filterDeletablePending(pending, usedAwbs);
        deleteCenterRecordsInChunks(deletable);
        return deletable.size();
    }

    @Transactional
    public long deleteAllPendingAwbs() {
        Set<String> usedAwbs = loadUsedAwbKeysNormalized();
        List<AwbCenterRecord> pending = mongoTemplate.find(
                Query.query(Criteria.where("queueStatus").is(AwbCenterRecord.QUEUE_PENDING)),
                AwbCenterRecord.class);
        List<AwbCenterRecord> deletable = filterDeletablePending(pending, usedAwbs);
        if (deletable.isEmpty()) {
            return 0;
        }
        Set<String> awbKeys = deletable.stream()
                .map(r -> CourierTrackingNumberValidator.normalizeOrNull(r.getAwbNumber()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        bulkDeleteRegistryPendingForAwbKeys(awbKeys);
        deleteCenterRecordsInChunks(deletable);
        return deletable.size();
    }

    /** Deletes PENDING AWB Center rows whose normalized AWB is in the given set. */
    @Transactional
    public void bulkDeletePendingCenterByAwbNumbers(Set<String> normalizedAwbs) {
        if (normalizedAwbs == null || normalizedAwbs.isEmpty()) {
            return;
        }
        List<AwbCenterRecord> pending = mongoTemplate.find(
                Query.query(Criteria.where("queueStatus").is(AwbCenterRecord.QUEUE_PENDING)),
                AwbCenterRecord.class);
        List<AwbCenterRecord> toDelete = pending.stream()
                .filter(r -> {
                    String n = CourierTrackingNumberValidator.normalizeOrNull(r.getAwbNumber());
                    return n != null && normalizedAwbs.contains(n);
                })
                .collect(Collectors.toList());
        deleteCenterRecordsInChunks(toDelete);
    }

    /**
     * Loads all AWB numbers in use across modules (4 queries, tracking fields only).
     */
    private Set<String> loadUsedAwbKeysNormalized() {
        Set<String> used = new HashSet<>();
        Query trackingOnly = new Query();
        trackingOnly.fields().include("trackingNumber");
        for (MonthlyCourierEntry e : mongoTemplate.find(trackingOnly, MonthlyCourierEntry.class)) {
            addNormalizedAwb(used, e.getTrackingNumber());
        }
        Query awbOnly = new Query();
        awbOnly.fields().include("awbNo");
        for (CollectionCenterEntry e : mongoTemplate.find(awbOnly, CollectionCenterEntry.class)) {
            addNormalizedAwb(used, e.getAwbNo());
        }
        for (CashBooking e : mongoTemplate.find(awbOnly, CashBooking.class)) {
            addNormalizedAwb(used, e.getAwbNo());
        }
        trackingOnly = new Query();
        trackingOnly.fields().include("trackingNumber");
        for (SmallClientEntry e : mongoTemplate.find(trackingOnly, SmallClientEntry.class)) {
            addNormalizedAwb(used, e.getTrackingNumber());
        }
        return used;
    }

    private static void addNormalizedAwb(Set<String> used, String raw) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(raw);
        if (norm != null && !norm.isBlank()) {
            used.add(norm);
        }
    }

    private static List<AwbCenterRecord> filterDeletablePending(List<AwbCenterRecord> pending, Set<String> usedAwbs) {
        return pending.stream()
                .filter(r -> {
                    String norm = CourierTrackingNumberValidator.normalizeOrNull(r.getAwbNumber());
                    return norm != null && !usedAwbs.contains(norm);
                })
                .collect(Collectors.toList());
    }

    private void bulkDeleteRegistryPendingForAwbKeys(Set<String> normalizedAwbs) {
        if (normalizedAwbs.isEmpty()) {
            return;
        }
        Query q = new Query(Criteria.where("status").ne(CollectionCustomerAwb.STATUS_USED));
        List<CollectionCustomerAwb> rows = mongoTemplate.find(q, CollectionCustomerAwb.class);
        List<CollectionCustomerAwb> toDelete = rows.stream()
                .filter(r -> {
                    String k = CourierTrackingNumberValidator.normalizeOrNull(r.getAwbNo());
                    return k != null && normalizedAwbs.contains(k);
                })
                .collect(Collectors.toList());
        deleteRegistryInChunks(toDelete);
    }

    private void deleteCenterRecordsInChunks(List<AwbCenterRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        List<String> ids = records.stream()
                .map(AwbCenterRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (int i = 0; i < ids.size(); i += DELETE_CHUNK_SIZE) {
            List<String> chunk = ids.subList(i, Math.min(i + DELETE_CHUNK_SIZE, ids.size()));
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(chunk)), AwbCenterRecord.class);
        }
    }

    private void deleteRegistryInChunks(List<CollectionCustomerAwb> records) {
        if (records.isEmpty()) {
            return;
        }
        List<String> ids = records.stream()
                .map(CollectionCustomerAwb::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (int i = 0; i < ids.size(); i += DELETE_CHUNK_SIZE) {
            List<String> chunk = ids.subList(i, Math.min(i + DELETE_CHUNK_SIZE, ids.size()));
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(chunk)), CollectionCustomerAwb.class);
        }
    }

    @Transactional
    public int syncAllQueuesFromModules() {
        int updated = 0;
        List<AwbCenterRecord> pending = mongoTemplate.find(
                Query.query(Criteria.where("queueStatus").is(AwbCenterRecord.QUEUE_PENDING)),
                AwbCenterRecord.class);
        for (AwbCenterRecord row : pending) {
            String before = row.getQueueStatus();
            syncUsageFromModules(row.getAwbNumber());
            AwbCenterRecord after = repository.findById(row.getId()).orElse(row);
            if (!before.equals(after.getQueueStatus())) {
                updated++;
            }
        }
        return updated;
    }

    private int resolveAwbFrequency() {
        Integer freq = companySettingsService.getSettings().getAwbFrequency();
        if (freq == null || freq < 1) {
            return 1;
        }
        return freq;
    }

    private String normalizeRequired(String raw) {
        String norm = CourierTrackingNumberValidator.normalizeOrNull(raw);
        if (norm == null) {
            throw new IllegalArgumentException("AWB number is required");
        }
        CourierTrackingNumberValidator.validateFormat(norm);
        if (!CourierTrackingNumberValidator.isPurelyNumeric(norm)) {
            throw new IllegalArgumentException("AWB must be numeric");
        }
        return norm;
    }

    private String resolveCollectionCustomerName(String awbNo) {
        if (!StringUtils.hasText(awbNo)) {
            return null;
        }
        Optional<CollectionCustomerAwb> mapping = collectionCustomerAwbRepository.findFirstByAwbNoIgnoreCase(awbNo.trim());
        if (mapping.isPresent()) {
            return customerRepository.findById(mapping.get().getCollectionCustomerId())
                    .map(com.app.billing.model.CollectionCustomer::getCustomerName)
                    .orElse(null);
        }
        return null;
    }

    private AwbCenterRecordDto toDto(AwbCenterRecord e) {
        String customerName = e.getCollectionCustomerName();
        String customerId = e.getCollectionCustomerId();
        if (Boolean.TRUE.equals(e.getCollectionCustomerAwb()) && !StringUtils.hasText(customerName)) {
            if (!StringUtils.hasText(e.getAwbNumber())) {
                customerName = null;
            } else {
                Optional<CollectionCustomerAwb> mapping = collectionCustomerAwbRepository.findFirstByAwbNoIgnoreCase(e.getAwbNumber().trim());
                if (mapping.isPresent()) {
                    customerId = mapping.get().getCollectionCustomerId();
                    customerName = customerRepository.findById(customerId)
                            .map(com.app.billing.model.CollectionCustomer::getCustomerName)
                            .orElse(null);
                }
            }
        }
        return AwbCenterRecordDto.builder()
                .id(e.getId())
                .awbNumber(e.getAwbNumber())
                .queueStatus(e.getQueueStatus())
                .createdAt(e.getCreatedAt())
                .completedAt(e.getCompletedAt())
                .usedInModule(e.getUsedInModule())
                .usedInReferenceId(e.getUsedInReferenceId())
                .collectionCustomerAwb(e.getCollectionCustomerAwb())
                .collectionCustomerId(customerId)
                .collectionCustomerName(customerName)
                .courierType(e.getCourierType())
                .lastUpdatedBy(e.getLastUpdatedBy())
                .build();
    }
}

package com.app.billing.service;

import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.dao.SmallClientEntryQuotationRepository;
import com.app.billing.dao.SmallClientRepository;
import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.model.ZoneConfiguration;
import com.app.billing.dto.SmallClientEntryDto;
import com.app.billing.dto.SmallClientEntryQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.model.SmallClientEntryQuotation;
import com.app.billing.model.SmallClient;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmallClientEntryQuotationService {

    private final SmallClientEntryQuotationRepository repository;
    private final SmallClientEntryRepository entryRepository;
    private final SmallClientRepository smallClientRepository;
    private final ZoneConfigurationRepository zoneConfigurationRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditUtil auditUtil;
    private final MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;
    private final InvoiceSequenceService invoiceSequenceService;

    @Transactional
    public SmallClientEntryQuotationDto create(SmallClientEntryQuotationDto dto) {
        String customerName = dto.getCustomerName();
        if (dto.getCustomerId() != null && !dto.getCustomerId().isBlank()) {
            SmallClient client = smallClientRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Small client not found: " + dto.getCustomerId()));
            customerName = client.getPartyName();
        } else if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Either Customer ID or Customer Name must be provided");
        }

        String shopId = dto.getShopId() != null ? dto.getShopId() : "DEFAULT_SHOP";

        SmallClientEntryQuotation entity = new SmallClientEntryQuotation();
        entity.setShopId(shopId);
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(customerName);
        entity.setAmountStatus(dto.getAmountStatus() != null && !dto.getAmountStatus().isBlank() ? dto.getAmountStatus() : "Pending");
        entity.setDescription(dto.getDescription());
        entity.setIsDownloaded(dto.getIsDownloaded() != null ? dto.getIsDownloaded() : false);
        entity.setMonth(dto.getMonth().toUpperCase());
        entity.setYear(dto.getYear());
        entity.setZone(dto.getZone());
        entity.setTitle(customerName + " - " + entity.getMonth() + " " + entity.getYear());
        LocalDate invDate = dto.getInvoiceDate() != null ? dto.getInvoiceDate() : LocalDate.now();
        entity.setInvoiceDate(invDate);
        if (StringUtils.hasText(dto.getInvoiceNumber())) {
            entity.setInvoiceNumber(dto.getInvoiceNumber().trim());
            entity.setInvoiceGenerated(true);
        } else {
            entity.setInvoiceNumber(null);
            entity.setInvoiceGenerated(false);
        }
        entity.setIsDownloaded(false);
        entity.setTotalShipments(0);
        entity.setTotalWeight(0.0);
        entity.setTotalAmount(0.0);
        entity.setPeriodSort(computePeriodSort(entity.getMonth(), entity.getYear()));

        auditUtil.setCreatedBy(entity);

        return toDto(repository.save(entity), List.of());
    }

    @Transactional
    public SmallClientEntryQuotationDto update(String id, SmallClientEntryQuotationDto dto) {
        SmallClientEntryQuotation existing = findEntityById(id);

        existing.setTitle(dto.getTitle());
        existing.setZone(dto.getZone());
        if (dto.getInvoiceDate() != null) {
            existing.setInvoiceDate(dto.getInvoiceDate());
        } else if (existing.getInvoiceDate() == null) {
            existing.setInvoiceDate(LocalDate.now());
        }
        if (dto.getInvoiceNumber() != null && StringUtils.hasText(dto.getInvoiceNumber())) {
            existing.setInvoiceNumber(dto.getInvoiceNumber().trim());
        }
        if (dto.getCustomerId() != null) {
            existing.setCustomerId(dto.getCustomerId());
        }
        if (dto.getMonth() != null) {
            existing.setMonth(dto.getMonth().toUpperCase());
        }
        if (dto.getYear() != null) {
            existing.setYear(dto.getYear());
        }
        if (dto.getCustomerName() != null) {
            existing.setCustomerName(dto.getCustomerName());
        }
        if (dto.getAmountStatus() != null) {
            existing.setAmountStatus(dto.getAmountStatus());
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        if (dto.getIsDownloaded() != null) {
            existing.setIsDownloaded(dto.getIsDownloaded());
        }
        existing.setNote(dto.getNote());
        applyChargeSettings(existing, dto);

        existing.setPeriodSort(computePeriodSort(existing.getMonth(), existing.getYear()));

        auditUtil.setUpdatedBy(existing);
        repository.save(existing);
        recalculateTotals(id);
        return toDtoWithEntries(findEntityById(id));
    }

    /**
     * Courier Quotation Report inline edits: amount status, received amount, and description (no full DTO merge).
     */
    @Transactional
    public SmallClientEntryQuotationDto updateAmountStatusAndDescription(String id, Map<String, Object> fields) {
        SmallClientEntryQuotation entity = findEntityById(id);
        List<SmallClientEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(entity.getId());
        double total = entries.isEmpty()
                ? (entity.getTotalAmount() != null ? entity.getTotalAmount() : 0.0)
                : invoiceGrandTotalService.computeInvoiceGrandTotal(calculationContext(entity), toMonthlyEntriesForTotals(entries));
        total = Math.round(total * 100.0) / 100.0;
        entity.setTotalAmount(total);

        if (fields.containsKey("receivedAmount") && fields.get("receivedAmount") != null && !fields.get("receivedAmount").toString().trim().isEmpty()) {
            double rec = Double.parseDouble(fields.get("receivedAmount").toString().trim());
            rec = Math.round(rec * 100.0) / 100.0;
            if (rec < 0) {
                throw new IllegalArgumentException("Received amount cannot be negative");
            }
            if (rec > total && total > 0) {
                throw new IllegalArgumentException("Received amount (₹" + rec + ") cannot exceed total amount (₹" + total + ")");
            }
            entity.setReceivedAmount(rec);
            double pending = Math.round(Math.max(0.0, total - rec) * 100.0) / 100.0;
            entity.setPendingAmount(pending);

            if (fields.containsKey("amountStatus") && fields.get("amountStatus") != null && !fields.get("amountStatus").toString().trim().isEmpty()) {
                entity.setAmountStatus(fields.get("amountStatus").toString().trim());
            } else {
                if (pending <= 0.0 && total > 0.0) {
                    entity.setAmountStatus("Paid");
                } else if (rec > 0.0 && pending > 0.0) {
                    entity.setAmountStatus("Partial");
                } else if (rec <= 0.0) {
                    entity.setAmountStatus("Pending");
                }
            }
        } else if (fields.containsKey("amountStatus") && fields.get("amountStatus") != null) {
            String st = fields.get("amountStatus").toString().trim();
            if (!st.isEmpty()) {
                entity.setAmountStatus(st);
                if ("Paid".equalsIgnoreCase(st)) {
                    entity.setReceivedAmount(total);
                    entity.setPendingAmount(0.0);
                } else if ("Pending".equalsIgnoreCase(st)) {
                    entity.setReceivedAmount(0.0);
                    entity.setPendingAmount(total);
                }
            }
        }

        if (fields.containsKey("description")) {
            entity.setDescription(fields.get("description") != null ? fields.get("description").toString() : "");
        }
        auditUtil.setUpdatedBy(entity);
        repository.save(entity);
        return toDtoWithEntries(findEntityById(id));
    }

    public SmallClientEntryQuotationDto findById(String id) {
        return toDtoWithEntries(findEntityById(id));
    }

    @Transactional
    public SmallClientEntryQuotationDto recordInvoiceActivity(String id, String action) {
        SmallClientEntryQuotation entity = findEntityById(id);
        String normalizedAction = normalizeInvoiceAction(action);
        if (entity.getInvoiceActivityLogs() == null) {
            entity.setInvoiceActivityLogs(new ArrayList<>());
        }
        entity.getInvoiceActivityLogs().add(new SmallClientEntryQuotation.InvoiceActivityLog(normalizedAction, java.time.LocalDateTime.now()));
        entity.setIsDownloaded(true);
        auditUtil.setUpdatedBy(entity);
        repository.save(entity);
        return toDtoWithEntries(entity);
    }

    private static String normalizeInvoiceAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "DOWNLOAD";
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VIEW", "PRINT", "DOWNLOAD" -> normalized;
            default -> "DOWNLOAD";
        };
    }

    private static final String[] MONTH_NAMES = { "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER" };

    public PageResponse<SmallClientEntryQuotationDto> findAll(String shopId, int page, int size) {
        return search(shopId, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, page, size);
    }

    /**
     * Paginated search with dynamic filters (omit null/blank params on the API side).
     * Zone matches quotation header {@code zone} or any shipment line whose {@code zone} is the same id
     * or resolves from zone display name.
     */
    public PageResponse<SmallClientEntryQuotationDto> search(String shopId, String title, String customerId,
            String zone, LocalDate fromDate, LocalDate toDate, String month, Integer year, String trackingNumber,
            String amountStatus, Boolean isDownloaded, Boolean invoiceGenerated, String invoiceNumber, String customerName,
            Integer invoiceMonth, Integer invoiceYear, LocalDate invoiceDateFrom, LocalDate invoiceDateTo,
            String description, String search,
            int page, int size) {
        String queryShopId = shopId != null ? shopId : "DEFAULT_SHOP";

        Query query = new Query();
        applyMonthlyListFilters(query, queryShopId, title, customerId, zone, fromDate, toDate, month, year,
                trackingNumber, amountStatus, isDownloaded, invoiceGenerated, invoiceNumber, customerName, invoiceMonth, invoiceYear, invoiceDateFrom,
                invoiceDateTo, description, search);

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        query.with(pageable);

        List<SmallClientEntryQuotation> list = mongoTemplate.find(query, SmallClientEntryQuotation.class);
        Query countQuery = new Query();
        applyMonthlyListFilters(countQuery, queryShopId, title, customerId, zone, fromDate, toDate, month, year,
                trackingNumber, amountStatus, isDownloaded, invoiceGenerated, invoiceNumber, customerName, invoiceMonth, invoiceYear, invoiceDateFrom,
                invoiceDateTo, description, search);
        long total = mongoTemplate.count(countQuery, SmallClientEntryQuotation.class);
        Page<SmallClientEntryQuotation> result = PageableExecutionUtils.getPage(list, pageable, () -> total);

        List<String> pageIds = list.stream().map(SmallClientEntryQuotation::getId).filter(Objects::nonNull).toList();
        Map<String, List<SmallClientEntry>> entriesByQuotationId = new HashMap<>();
        if (!pageIds.isEmpty()) {
            for (SmallClientEntry row : entryRepository.findByMonthlyQuotationIdIn(pageIds)) {
                if (row.getMonthlyQuotationId() != null) {
                    entriesByQuotationId
                            .computeIfAbsent(row.getMonthlyQuotationId(), __ -> new ArrayList<>())
                            .add(row);
                }
            }
        }

        return PaginationUtil.toPageResponse(
                result.map(q -> toDto(q, entriesByQuotationId.getOrDefault(q.getId(), List.of()))));
    }

    private List<String> monthlyQuotationIdsMatchingZone(String zoneParam) {
        Set<String> zoneKeys = new LinkedHashSet<>();
        zoneKeys.add(zoneParam.trim());
        for (ZoneConfiguration z : zoneConfigurationRepository.findByIsDeletedFalse()) {
            if (z.getZoneName() != null && zoneParam.trim().equalsIgnoreCase(z.getZoneName().trim())) {
                zoneKeys.add(z.getId());
            }
        }
        Query entryQ = new Query(Criteria.where("zone").in(zoneKeys));
        return mongoTemplate.find(entryQ, SmallClientEntry.class).stream()
                .map(SmallClientEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void applyMonthlyListFilters(Query query, String queryShopId, String title, String customerId,
            String zone, LocalDate fromDate, LocalDate toDate, String month, Integer year, String trackingNumber,
            String amountStatus, Boolean isDownloaded, Boolean invoiceGenerated, String invoiceNumber, String customerName,
            Integer invoiceMonth, Integer invoiceYear, LocalDate invoiceDateFrom, LocalDate invoiceDateTo,
            String description, String search) {
        query.addCriteria(Criteria.where("shopId").is(queryShopId));

        if (StringUtils.hasText(search)) {
            applyGlobalSearchCriteria(query, search.trim());
        }

        if (StringUtils.hasText(title)) {
            query.addCriteria(Criteria.where("title").regex(Pattern.compile(".*" + Pattern.quote(title.trim()) + ".*",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL)));
        }
        if (StringUtils.hasText(customerId)) {
            query.addCriteria(Criteria.where("customerId").is(customerId.trim()));
        }
        if (StringUtils.hasText(zone)) {
            List<String> idsFromEntries = monthlyQuotationIdsMatchingZone(zone.trim());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("zone").is(zone.trim()),
                    Criteria.where("id").in(idsFromEntries.isEmpty() ? List.of("NO_MATCH") : idsFromEntries)));
        }
        if (fromDate != null && toDate != null) {
            List<Criteria> monthYearCriteria = new ArrayList<>();
            YearMonth start = YearMonth.from(fromDate);
            YearMonth end = YearMonth.from(toDate);
            for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                monthYearCriteria.add(Criteria.where("year").is(ym.getYear())
                        .and("month").regex("^" + MONTH_NAMES[ym.getMonthValue() - 1] + "$", "i"));
            }
            if (!monthYearCriteria.isEmpty()) {
                query.addCriteria(new Criteria().orOperator(monthYearCriteria.toArray(new Criteria[0])));
            }
        } else {
            if (StringUtils.hasText(month)) {
                addFlexibleMonthCriteria(query, month.trim());
            }
            if (year != null) {
                query.addCriteria(Criteria.where("year").is(year));
            }
        }
        if (StringUtils.hasText(trackingNumber)) {
            List<String> validIds = findQuotationIdsByShipmentLineContains(trackingNumber.trim());
            query.addCriteria(Criteria.where("id").in(validIds.isEmpty() ? List.of("NO_MATCH") : validIds));
        }
        if (StringUtils.hasText(amountStatus)) {
            query.addCriteria(Criteria.where("amountStatus").regex(
                    Pattern.compile(".*" + Pattern.quote(amountStatus.trim()) + ".*", Pattern.CASE_INSENSITIVE)));
        }
        if (Boolean.TRUE.equals(isDownloaded)) {
            query.addCriteria(Criteria.where("isDownloaded").is(true));
        }
        if (Boolean.TRUE.equals(invoiceGenerated)) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("invoiceGenerated").is(true),
                    new Criteria().andOperator(
                            Criteria.where("invoiceGenerated").ne(false),
                            Criteria.where("invoiceNumber").exists(true).ne(null).ne("")
                    )
            ));
            query.addCriteria(Criteria.where("excludedFromReport").ne(true));
        }
        if (StringUtils.hasText(invoiceNumber)) {
            query.addCriteria(Criteria.where("invoiceNumber").regex(
                    Pattern.compile(".*" + Pattern.quote(invoiceNumber.trim()) + ".*", Pattern.CASE_INSENSITIVE)));
        }
        if (StringUtils.hasText(customerName)) {
            query.addCriteria(Criteria.where("customerName").regex(
                    Pattern.compile(".*" + Pattern.quote(customerName.trim()) + ".*", Pattern.CASE_INSENSITIVE)));
        }
        applyInvoiceDateCriteria(query, queryShopId, invoiceMonth, invoiceYear, invoiceDateFrom, invoiceDateTo);
        if (StringUtils.hasText(description)) {
            query.addCriteria(Criteria.where("description").regex(
                    Pattern.compile(".*" + Pattern.quote(description.trim()) + ".*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)));
        }
    }

    private void applyInvoiceDateCriteria(Query query, String queryShopId, Integer invoiceMonth, Integer invoiceYear,
            LocalDate invoiceDateFrom, LocalDate invoiceDateTo) {
        Integer normalizedMonth = invoiceMonth != null && invoiceMonth >= 1 && invoiceMonth <= 12 ? invoiceMonth : null;
        LocalDate from = invoiceDateFrom;
        LocalDate to = invoiceDateTo;

        if (normalizedMonth != null && invoiceYear != null) {
            YearMonth ym = YearMonth.of(invoiceYear, normalizedMonth);
            from = maxDate(from, ym.atDay(1));
            to = minDate(to, ym.atEndOfMonth());
        } else if (invoiceYear != null) {
            from = maxDate(from, LocalDate.of(invoiceYear, 1, 1));
            to = minDate(to, LocalDate.of(invoiceYear, 12, 31));
        }

        if (from != null || to != null) {
            Criteria inv = Criteria.where("invoiceDate");
            if (from != null && to != null) {
                inv.gte(from).lte(to);
            } else if (from != null) {
                inv.gte(from);
            } else {
                inv.lte(to);
            }
            query.addCriteria(inv);
        }

        if (normalizedMonth != null && invoiceYear == null) {
            List<String> matchingIds = findQuotationIdsByInvoiceMonth(queryShopId, normalizedMonth);
            query.addCriteria(Criteria.where("id").in(matchingIds.isEmpty() ? List.of("NO_MATCH") : matchingIds));
        }
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return a.isBefore(b) ? a : b;
    }

    private List<String> findQuotationIdsByInvoiceMonth(String shopId, int invoiceMonth) {
        Query q = new Query(Criteria.where("shopId").is(shopId).and("invoiceDate").ne(null));
        return mongoTemplate.find(q, SmallClientEntryQuotation.class).stream()
                .filter(x -> x.getInvoiceDate() != null && x.getInvoiceDate().getMonthValue() == invoiceMonth)
                .map(SmallClientEntryQuotation::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Quotation ids whose shipment lines match AWB, destination, or courier (substring, case-insensitive). */
    private List<String> findQuotationIdsByShipmentLineContains(String fragment) {
        if (!StringUtils.hasText(fragment)) {
            return List.of();
        }
        String tn = fragment.trim();
        LinkedHashSet<String> quotationIds = new LinkedHashSet<>();
        entryRepository.findByTrackingNumberContainingIgnoreCase(tn).stream()
                .map(SmallClientEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .forEach(quotationIds::add);
        entryRepository.findByConsigneeAddressContainingIgnoreCase(tn).stream()
                .map(SmallClientEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .forEach(quotationIds::add);
        entryRepository.findByCourierTypeContainingIgnoreCase(tn).stream()
                .map(SmallClientEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .forEach(quotationIds::add);
        return new ArrayList<>(quotationIds);
    }

    /** Quotation ids with at least one entry in a zone whose id or display name contains the token. */
    private List<String> findQuotationIdsByZoneTextToken(String token) {
        if (!StringUtils.hasText(token)) {
            return List.of();
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        Set<String> zoneKeys = new LinkedHashSet<>();
        for (ZoneConfiguration z : zoneConfigurationRepository.findByIsDeletedFalse()) {
            if (z.getId() != null && z.getId().toLowerCase(Locale.ROOT).contains(t)) {
                zoneKeys.add(z.getId());
            }
            if (z.getZoneName() != null && z.getZoneName().toLowerCase(Locale.ROOT).contains(t)) {
                zoneKeys.add(z.getId());
            }
        }
        if (zoneKeys.isEmpty()) {
            return List.of();
        }
        Query entryQ = new Query(Criteria.where("zone").in(zoneKeys));
        return mongoTemplate.find(entryQ, SmallClientEntry.class).stream()
                .map(SmallClientEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Single search box: customer name, month, year, zone (header or via shipment zones), title, invoice,
     * description, note, status, totals, weight, and shipment lines (AWB / destination / courier).
     */
    private void applyGlobalSearchCriteria(Query query, String s) {
        List<Criteria> ors = new ArrayList<>();
        ors.add(Criteria.where("title").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)));
        ors.add(Criteria.where("customerName").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)));

        ors.add(Criteria.where("zone").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE)));
        List<String> zoneQuotIds = findQuotationIdsByZoneTextToken(s);
        if (!zoneQuotIds.isEmpty()) {
            ors.add(Criteria.where("id").in(zoneQuotIds));
        }

        if (!s.matches("^\\d+$")) {
            ors.add(Criteria.where("month").regex(Pattern.compile("^" + Pattern.quote(s), Pattern.CASE_INSENSITIVE)));
        } else {
            try {
                int m = Integer.parseInt(s);
                if (m >= 1 && m <= 12) {
                    ors.add(Criteria.where("month").regex("^" + Pattern.quote(MONTH_NAMES[m - 1]) + "$", "i"));
                }
            } catch (NumberFormatException ignored) {
                // never
            }
        }

        if (s.matches("^\\d{4}$")) {
            try {
                ors.add(Criteria.where("year").is(Integer.parseInt(s)));
            } catch (NumberFormatException ignored) {
                // never
            }
        }

        if (s.matches("^\\d+$") && s.length() != 4) {
            try {
                ors.add(Criteria.where("totalShipments").is(Integer.parseInt(s)));
            } catch (NumberFormatException ignored) {
                // never
            }
        }

        if (s.matches("^\\d+(\\.\\d+)?$")) {
            try {
                double amt = Double.parseDouble(s);
                ors.add(Criteria.where("totalAmount").gte(amt - 0.005).lte(amt + 0.005));
                ors.add(Criteria.where("totalWeight").gte(amt - 0.005).lte(amt + 0.005));
            } catch (NumberFormatException ignored) {
                // never
            }
        }

        ors.add(Criteria.where("invoiceNumber").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE)));
        ors.add(Criteria.where("description").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)));
        ors.add(Criteria.where("note").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)));
        ors.add(Criteria.where("amountStatus").regex(Pattern.compile(".*" + Pattern.quote(s) + ".*",
                Pattern.CASE_INSENSITIVE)));

        List<String> shipQuotIds = findQuotationIdsByShipmentLineContains(s);
        if (!shipQuotIds.isEmpty()) {
            ors.add(Criteria.where("id").in(shipQuotIds));
        }

        if (!ors.isEmpty()) {
            query.addCriteria(new Criteria().orOperator(ors.toArray(new Criteria[0])));
        }
    }

    /** Match stored month (e.g. JAN, JANUARY) from shorthand or number 1â€“12. */
    private void addFlexibleMonthCriteria(Query query, String monthInput) {
        List<Criteria> ors = new ArrayList<>();
        String tl = monthInput.toLowerCase(Locale.ROOT);
        for (String mn : MONTH_NAMES) {
            if (mn.toLowerCase(Locale.ROOT).startsWith(tl) || mn.equalsIgnoreCase(monthInput.trim())) {
                ors.add(Criteria.where("month").regex(Pattern.compile("^" + Pattern.quote(mn) + "$",
                        Pattern.CASE_INSENSITIVE)));
            }
        }
        try {
            int m = Integer.parseInt(monthInput.trim());
            if (m >= 1 && m <= 12) {
                ors.add(Criteria.where("month").regex("^" + Pattern.quote(MONTH_NAMES[m - 1]) + "$", "i"));
            }
        } catch (NumberFormatException ignored) {
            // not a number
        }
        if (ors.isEmpty()) {
            query.addCriteria(Criteria.where("month").regex("^" + Pattern.quote(monthInput.toUpperCase(Locale.ROOT))
                    + "$", "i"));
        } else if (ors.size() == 1) {
            query.addCriteria(ors.get(0));
        } else {
            query.addCriteria(new Criteria().orOperator(ors.toArray(new Criteria[0])));
        }
    }

    @Transactional
    public void delete(String id) {
        SmallClientEntryQuotation existing = findEntityById(id);

        // Soft delete could involve changing status to DELETED, but since requirements
        // mention soft delete, we'll implement it if there is a status for it, else
        // hard delete.
        // Assuming we delete entries too.
        entryRepository.deleteByMonthlyQuotationId(id);
        repository.delete(existing);
    }

    /**
     * Delete record from Report view ONLY.
     * Sets excludedFromReport = true without deleting the SmallClientEntryQuotation or its entries.
     */
    @Transactional
    public void deleteFromReport(String id) {
        SmallClientEntryQuotation existing = findEntityById(id);
        existing.setExcludedFromReport(true);
        auditUtil.setUpdatedBy(existing);
        repository.save(existing);
    }

    @Transactional
    public SmallClientEntryQuotationDto generateInvoice(String quotationId) {
        SmallClientEntryQuotation entity = findEntityById(quotationId);

        LocalDate invDate = entity.getInvoiceDate() != null ? entity.getInvoiceDate() : LocalDate.now();
        entity.setInvoiceDate(invDate);

        String monthName = invDate.getMonth().name();
        int year = invDate.getYear();
        int periodSort = year * 100 + invDate.getMonthValue();

        entity.setMonth(monthName);
        entity.setYear(year);
        entity.setPeriodSort(periodSort);

        if (!StringUtils.hasText(entity.getInvoiceNumber())) {
            String invNum = generateNextInvoiceNumberForDate(invDate);
            entity.setInvoiceNumber(invNum);
        }

        entity.setInvoiceGenerated(true);
        entity.setExcludedFromReport(false);

        String shopId = entity.getShopId() != null ? entity.getShopId() : "DEFAULT_SHOP";
        String currentInvoiceNum = entity.getInvoiceNumber().trim();
        List<SmallClientEntryQuotation> duplicates = repository.findAll().stream()
                .filter(q -> !q.getId().equals(quotationId))
                .filter(q -> Objects.equals(q.getShopId() != null ? q.getShopId() : "DEFAULT_SHOP", shopId))
                .filter(q -> StringUtils.hasText(q.getInvoiceNumber()) && q.getInvoiceNumber().trim().equalsIgnoreCase(currentInvoiceNum))
                .filter(q -> Boolean.TRUE.equals(q.getInvoiceGenerated()) && !Boolean.TRUE.equals(q.getExcludedFromReport()))
                .toList();

        for (SmallClientEntryQuotation dup : duplicates) {
            log.info("Excluding duplicate small client entry report record id={} matching invoiceNumber={}", dup.getId(), currentInvoiceNum);
            dup.setExcludedFromReport(true);
            auditUtil.setUpdatedBy(dup);
            repository.save(dup);
        }

        auditUtil.setUpdatedBy(entity);
        repository.save(entity);
        recalculateTotals(quotationId);
        return toDtoWithEntries(findEntityById(quotationId));
    }


    @Transactional
    public void recalculateTotals(String quotationId) {
        SmallClientEntryQuotation quotation = findEntityById(quotationId);
        List<SmallClientEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(quotationId);

        int shipments = entries.size();
        double weight = entries.stream().mapToDouble(e -> e.getWeight() != null ? e.getWeight() : 0.0).sum();
        MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext ctx = calculationContext(quotation);
        double amount = invoiceGrandTotalService.computeInvoiceGrandTotal(ctx, toMonthlyEntriesForTotals(entries));
        amount = Math.round(amount * 100.0) / 100.0;

        quotation.setTotalShipments(shipments);
        quotation.setTotalWeight(weight);
        quotation.setTotalAmount(amount);

        double rec = quotation.getReceivedAmount() != null ? Math.round(quotation.getReceivedAmount() * 100.0) / 100.0 : 0.0;
        double pending = Math.round(Math.max(0.0, amount - rec) * 100.0) / 100.0;

        quotation.setReceivedAmount(rec);
        quotation.setPendingAmount(pending);

        if (pending <= 0.0 && amount > 0.0) {
            quotation.setAmountStatus("Paid");
            quotation.setReceivedAmount(amount);
            quotation.setPendingAmount(0.0);
        } else if (rec > 0.0 && pending > 0.0) {
            quotation.setAmountStatus("Partial");
        } else {
            quotation.setAmountStatus("Pending");
        }

        repository.save(quotation);
    }

    private static MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext calculationContext(
            SmallClientEntryQuotation quotation) {
        return MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                quotation.getCustomerId(),
                quotation.getFuelChargePercentage(),
                quotation.getFovCharges(),
                quotation.getGstPercentage(),
                quotation.getIncludeFuel(),
                quotation.getIncludeGst(),
                quotation.getIncludeFov());
    }

    private static void applyChargeSettings(SmallClientEntryQuotation entity, SmallClientEntryQuotationDto dto) {
        MonthlyCourierInvoiceGrandTotalService.validateGstPercentage(dto.getGstPercentage());
        entity.setFuelChargePercentage(dto.getFuelChargePercentage());
        entity.setFovCharges(dto.getFovCharges());
        entity.setGstPercentage(dto.getGstPercentage());
        if (dto.getIncludeFuel() != null) {
            entity.setIncludeFuel(dto.getIncludeFuel());
        }
        if (dto.getIncludeGst() != null) {
            entity.setIncludeGst(dto.getIncludeGst());
        }
        if (dto.getIncludeFov() != null) {
            entity.setIncludeFov(dto.getIncludeFov());
        }
    }

    public SmallClientEntryQuotation findEntityById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Small Client Entry quotation not found: " + id));
    }

    /** Financial year string from date: Apr–Mar. e.g. 2026-04-01 -> "2026-27". */
    public static String getFinancialYearStr(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int fyStart = (month >= 4) ? year : year - 1;
        int fyEnd = (fyStart + 1) % 100;
        return fyStart + "-" + String.format("%02d", fyEnd);
    }

    /** Next invoice number for the financial year of the given date. Format: 001/2026. */
    public String generateNextInvoiceNumberForDate(LocalDate invoiceDate) {
        return invoiceSequenceService.generateNextInvoiceNumber(invoiceDate);
    }

    public String getOrGenerateInvoiceNumber(String id) {
        SmallClientEntryQuotation q = findEntityById(id);
        if (q.getInvoiceNumber() != null && !q.getInvoiceNumber().isEmpty()) {
            return q.getInvoiceNumber();
        }
        LocalDate date = q.getInvoiceDate() != null ? q.getInvoiceDate() : LocalDate.now();
        if (q.getInvoiceDate() == null) {
            q.setInvoiceDate(date);
        }
        String invNum = generateNextInvoiceNumberForDate(date);
        q.setInvoiceNumber(invNum);
        repository.save(q);
        return invNum;
    }

    /** Returns invoice date for the quotation; if null, returns today. */
    public LocalDate getInvoiceDate(String id) {
        SmallClientEntryQuotation q = findEntityById(id);
        return q.getInvoiceDate() != null ? q.getInvoiceDate() : LocalDate.now();
    }

    /**
     * {@code year * 100 + monthIndex} (January = 1) for stable month/year ordering in MongoDB sorts.
     */
    static Integer computePeriodSort(String month, Integer year) {
        if (year == null) {
            return null;
        }
        int mi = monthIndex(month);
        return year * 100 + mi;
    }

    private static int monthIndex(String month) {
        if (month == null || month.isBlank()) {
            return 0;
        }
        String u = month.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (MONTH_NAMES[i].equals(u)) {
                return i + 1;
            }
        }
        return 0;
    }

    private SmallClientEntryQuotationDto toDtoWithEntries(SmallClientEntryQuotation entity) {
        List<SmallClientEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(entity.getId());
        return toDto(entity, entries);
    }

    private SmallClientEntryQuotationDto toDto(SmallClientEntryQuotation entity, List<SmallClientEntry> entries) {
        SmallClientEntryQuotationDto dto = new SmallClientEntryQuotationDto();
        dto.setId(entity.getId());
        dto.setShopId(entity.getShopId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setTitle(entity.getTitle());
        dto.setMonth(entity.getMonth());
        dto.setYear(entity.getYear());
        dto.setTotalShipments(entity.getTotalShipments());
        dto.setTotalWeight(entity.getTotalWeight());
        double displayTotal = entries == null || entries.isEmpty()
                ? (entity.getTotalAmount() != null ? entity.getTotalAmount() : 0.0)
                : invoiceGrandTotalService.computeInvoiceGrandTotal(calculationContext(entity), toMonthlyEntriesForTotals(entries));
        displayTotal = Math.round(displayTotal * 100.0) / 100.0;
        dto.setTotalAmount(displayTotal);
        double rec = entity.getReceivedAmount() != null ? entity.getReceivedAmount()
                : ("Paid".equalsIgnoreCase(entity.getAmountStatus()) ? displayTotal : 0.0);
        rec = Math.round(rec * 100.0) / 100.0;
        double pending = Math.round(Math.max(0.0, displayTotal - rec) * 100.0) / 100.0;
        dto.setReceivedAmount(rec);
        dto.setPendingAmount(pending);
        dto.setFuelChargePercentage(entity.getFuelChargePercentage());
        dto.setFovCharges(entity.getFovCharges());
        dto.setGstPercentage(entity.getGstPercentage());
        dto.setIncludeFuel(entity.getIncludeFuel());
        dto.setIncludeGst(entity.getIncludeGst());
        dto.setIncludeFov(entity.getIncludeFov());
        dto.setInvoiceNumber(entity.getInvoiceNumber());
        dto.setInvoiceDate(entity.getInvoiceDate());
        dto.setZone(entity.getZone());
        dto.setAmountStatus(entity.getAmountStatus() != null ? entity.getAmountStatus() : "Pending");
        dto.setDescription(entity.getDescription());
        dto.setIsDownloaded(entity.getIsDownloaded() != null ? entity.getIsDownloaded() : false);
        boolean isGen = Boolean.TRUE.equals(entity.getInvoiceGenerated()) ||
                (StringUtils.hasText(entity.getInvoiceNumber()));
        dto.setInvoiceGenerated(isGen);
        dto.setInvoiceActivityLogs(entity.getInvoiceActivityLogs() != null ? entity.getInvoiceActivityLogs() : new ArrayList<>());
        dto.setNote(entity.getNote());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        return dto;
    }

    private static List<MonthlyCourierEntry> toMonthlyEntriesForTotals(List<SmallClientEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream().map(SmallClientEntryQuotationService::toMonthlyEntryForTotals).toList();
    }

    private static MonthlyCourierEntry toMonthlyEntryForTotals(SmallClientEntry e) {
        MonthlyCourierEntry m = new MonthlyCourierEntry();
        m.setAmount(e.getAmount());
        m.setAdditionalCharges(e.getAdditionalCharges());
        m.setGstApplicable(e.getGstApplicable());
        m.setFuelApplicable(e.getFuelApplicable());
        m.setFovApplicable(e.getFovApplicable());
        return m;
    }
}


package com.app.billing.service;

import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dao.MonthlyCourierQuotationRepository;
import com.app.billing.dao.ClientRepository;
import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.model.ZoneConfiguration;
import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.MonthlyCourierQuotation;
import com.app.billing.model.Client;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.ClientEntryEditLockService;
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
public class MonthlyCourierQuotationService {

    private final MonthlyCourierQuotationRepository repository;
    private final MonthlyCourierEntryRepository entryRepository;
    private final ClientRepository clientRepository;
    private final ZoneConfigurationRepository zoneConfigurationRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditUtil auditUtil;
    private final MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;
    private final ClientEntryEditLockService clientEntryEditLockService;
    private final InvoiceSequenceService invoiceSequenceService;

    @Transactional
    public MonthlyCourierQuotationDto create(MonthlyCourierQuotationDto dto) {
        String customerName = dto.getCustomerName();
        if (dto.getCustomerId() != null && !dto.getCustomerId().isBlank()) {
            Client client = clientRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + dto.getCustomerId()));
            customerName = client.getPartyName();
        } else if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("Either Customer ID or Customer Name must be provided");
        }

        String shopId = dto.getShopId() != null ? dto.getShopId() : "DEFAULT_SHOP";

        MonthlyCourierQuotation entity = new MonthlyCourierQuotation();
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
        // Client Entry save must NOT create or persist an Invoice or generate an Invoice Number.
        // Invoices are created only when the user explicitly clicks "Generate Invoice".
        entity.setInvoiceNumber(null);
        entity.setInvoiceGenerated(false);
        entity.setIsDownloaded(false);
        entity.setTotalShipments(0);
        entity.setTotalWeight(0.0);
        entity.setBaseAmount(0.0);
        entity.setTotalAmount(0.0);
        entity.setPeriodSort(computePeriodSort(entity.getMonth(), entity.getYear()));
        entity.setApplyQuotationRates(dto.getApplyQuotationRates() != null ? dto.getApplyQuotationRates() : true);

        auditUtil.setCreatedBy(entity);

        return toDto(repository.save(entity), List.of());
    }

    @Transactional
    public MonthlyCourierQuotationDto update(String id, MonthlyCourierQuotationDto dto) {
        MonthlyCourierQuotation existing = findEntityById(id);
        clientEntryEditLockService.enforceEditAllowed(existing.getMonth(), existing.getYear());

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
        // Do NOT auto-generate invoice number on update if blank; invoice generation must be explicit.
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
        if (dto.getApplyQuotationRates() != null) {
            existing.setApplyQuotationRates(dto.getApplyQuotationRates());
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
     * Courier Quotation Report inline edits: only amount status and description (no full DTO merge).
     */
    @Transactional
    public MonthlyCourierQuotationDto updateAmountStatusAndDescription(String id, Map<String, String> fields) {
        MonthlyCourierQuotation entity = findEntityById(id);
        clientEntryEditLockService.enforceEditAllowed(entity.getMonth(), entity.getYear());
        if (fields.containsKey("amountStatus") && fields.get("amountStatus") != null) {
            entity.setAmountStatus(fields.get("amountStatus").trim());
        }
        if (fields.containsKey("description")) {
            entity.setDescription(fields.get("description"));
        }
        auditUtil.setUpdatedBy(entity);
        repository.save(entity);
        return toDtoWithEntries(findEntityById(id));
    }

    public MonthlyCourierQuotationDto findById(String id) {
        return toDtoWithEntries(findEntityById(id));
    }

    @Transactional
    public MonthlyCourierQuotationDto recordInvoiceActivity(String id, String action) {
        MonthlyCourierQuotation entity = findEntityById(id);
        String normalizedAction = normalizeInvoiceAction(action);
        if (entity.getInvoiceActivityLogs() == null) {
            entity.setInvoiceActivityLogs(new ArrayList<>());
        }
        entity.getInvoiceActivityLogs().add(new MonthlyCourierQuotation.InvoiceActivityLog(normalizedAction, java.time.LocalDateTime.now()));
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

    public PageResponse<MonthlyCourierQuotationDto> findAll(String shopId, int page, int size) {
        return search(shopId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, page, size);
    }

    /**
     * Paginated search with dynamic filters (omit null/blank params on the API side).
     * Zone matches quotation header {@code zone} or any shipment line whose {@code zone} is the same id
     * or resolves from zone display name.
     */
    public PageResponse<MonthlyCourierQuotationDto> search(String shopId, String title, String customerId,
            String zone, LocalDate fromDate, LocalDate toDate, String month, Integer year, String trackingNumber,
            String amountStatus, Boolean isDownloaded, String invoiceNumber, String customerName,
            Integer invoiceMonth, Integer invoiceYear, LocalDate invoiceDateFrom, LocalDate invoiceDateTo,
            String description, String search,
            int page, int size) {
        return search(shopId, title, customerId, zone, fromDate, toDate, month, year,
                trackingNumber, amountStatus, isDownloaded, null, invoiceNumber, customerName,
                invoiceMonth, invoiceYear, invoiceDateFrom, invoiceDateTo, description, search, page, size);
    }

    public PageResponse<MonthlyCourierQuotationDto> search(String shopId, String title, String customerId,
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

        List<MonthlyCourierQuotation> list = mongoTemplate.find(query, MonthlyCourierQuotation.class);
        Query countQuery = new Query();
        applyMonthlyListFilters(countQuery, queryShopId, title, customerId, zone, fromDate, toDate, month, year,
                trackingNumber, amountStatus, isDownloaded, invoiceGenerated, invoiceNumber, customerName, invoiceMonth, invoiceYear, invoiceDateFrom,
                invoiceDateTo, description, search);
        long total = mongoTemplate.count(countQuery, MonthlyCourierQuotation.class);
        Page<MonthlyCourierQuotation> result = PageableExecutionUtils.getPage(list, pageable, () -> total);

        List<String> pageIds = list.stream().map(MonthlyCourierQuotation::getId).filter(Objects::nonNull).toList();
        Map<String, List<MonthlyCourierEntry>> entriesByQuotationId = new HashMap<>();
        if (!pageIds.isEmpty()) {
            for (MonthlyCourierEntry row : entryRepository.findByMonthlyQuotationIdIn(pageIds)) {
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
        return mongoTemplate.find(entryQ, MonthlyCourierEntry.class).stream()
                .map(MonthlyCourierEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void applyMonthlyListFilters(Query query, String queryShopId, String title, String customerId,
            String zone, LocalDate fromDate, LocalDate toDate, String month, Integer year, String trackingNumber,
            String amountStatus, Boolean isDownloaded, Boolean invoiceGenerated, String invoiceNumber, String customerName,
            Integer invoiceMonth, Integer invoiceYear, LocalDate invoiceDateFrom, LocalDate invoiceDateTo,
            String description, String search) {
        if ("DEFAULT_SHOP".equals(queryShopId)) {
            query.addCriteria(Criteria.where("shopId").in("DEFAULT_SHOP", null));
        } else {
            query.addCriteria(Criteria.where("shopId").is(queryShopId));
        }

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
                query.addCriteria(Criteria.where("year").in(year, String.valueOf(year)));
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
        return mongoTemplate.find(q, MonthlyCourierQuotation.class).stream()
                .filter(x -> x.getInvoiceDate() != null && x.getInvoiceDate().getMonthValue() == invoiceMonth)
                .map(MonthlyCourierQuotation::getId)
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
                .map(MonthlyCourierEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .forEach(quotationIds::add);
        entryRepository.findByConsigneeAddressContainingIgnoreCase(tn).stream()
                .map(MonthlyCourierEntry::getMonthlyQuotationId)
                .filter(Objects::nonNull)
                .forEach(quotationIds::add);
        entryRepository.findByCourierTypeContainingIgnoreCase(tn).stream()
                .map(MonthlyCourierEntry::getMonthlyQuotationId)
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
        return mongoTemplate.find(entryQ, MonthlyCourierEntry.class).stream()
                .map(MonthlyCourierEntry::getMonthlyQuotationId)
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

    /** Match stored month (e.g. JAN, JANUARY) from shorthand or number 1–12. */
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
        MonthlyCourierQuotation existing = findEntityById(id);

        // Soft delete could involve changing status to DELETED, but since requirements
        // mention soft delete, we'll implement it if there is a status for it, else
        // hard delete.
        // Assuming we delete entries too.
        entryRepository.deleteByMonthlyQuotationId(id);
        repository.delete(existing);
    }

    /**
     * Delete record from Report view ONLY.
     * Sets excludedFromReport = true without deleting the MonthlyCourierQuotation or its entries.
     */
    @Transactional
    public void deleteFromReport(String id) {
        MonthlyCourierQuotation existing = findEntityById(id);
        existing.setExcludedFromReport(true);
        auditUtil.setUpdatedBy(existing);
        repository.save(existing);
    }

    @Transactional
    public MonthlyCourierQuotationDto generateInvoice(String quotationId) {
        MonthlyCourierQuotation entity = findEntityById(quotationId);

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
        List<MonthlyCourierQuotation> duplicates = repository.findAll().stream()
                .filter(q -> !q.getId().equals(quotationId))
                .filter(q -> Objects.equals(q.getShopId() != null ? q.getShopId() : "DEFAULT_SHOP", shopId))
                .filter(q -> Objects.equals(q.getCustomerId(), entity.getCustomerId()))
                .filter(q -> monthName.equalsIgnoreCase(q.getMonth()))
                .filter(q -> Objects.equals(q.getYear(), year))
                .filter(q -> Boolean.TRUE.equals(q.getInvoiceGenerated()) && !Boolean.TRUE.equals(q.getExcludedFromReport()))
                .toList();

        for (MonthlyCourierQuotation dup : duplicates) {
            log.info("Excluding duplicate report record id={} for customer={}, period={}-{}", dup.getId(), dup.getCustomerId(), monthName, year);
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
        MonthlyCourierQuotation quotation = findEntityById(quotationId);
        List<MonthlyCourierEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(quotationId);

        int shipments = entries.size();
        double weight = entries.stream().mapToDouble(e -> e.getWeight() != null ? e.getWeight() : 0.0).sum();
        MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext ctx = calculationContext(quotation);
        MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown breakdown = invoiceGrandTotalService.computeInvoiceBreakdown(ctx, entries);

        double totalAmount = breakdown.totalRevenue().doubleValue();
        quotation.setTotalShipments(shipments);
        quotation.setTotalWeight(weight);
        quotation.setBaseAmount(breakdown.baseRevenue().doubleValue());
        quotation.setTotalAmount(totalAmount);

        repository.save(quotation);
    }

    private static MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext calculationContext(
            MonthlyCourierQuotation quotation) {
        return MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                quotation.getCustomerId(),
                quotation.getFuelChargePercentage(),
                quotation.getFovCharges(),
                quotation.getGstPercentage(),
                quotation.getIncludeFuel(),
                quotation.getIncludeGst(),
                quotation.getIncludeFov(),
                quotation.getDiscountType(),
                quotation.getDiscountValue(),
                quotation.getAdditionalCharges());
    }

    private static void applyChargeSettings(MonthlyCourierQuotation entity, MonthlyCourierQuotationDto dto) {
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
        entity.setDiscountType(dto.getDiscountType());
        entity.setDiscountValue(dto.getDiscountValue());
        entity.setDiscountAmount(dto.getDiscountAmount());
        entity.setAdditionalCharges(dto.getAdditionalCharges());
        entity.setDiscountDescription(dto.getDiscountDescription());
        entity.setAdditionalChargesDescription(dto.getAdditionalChargesDescription());
    }

    public MonthlyCourierQuotation findEntityById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monthly courier quotation not found: " + id));
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
        MonthlyCourierQuotation q = findEntityById(id);
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
        MonthlyCourierQuotation q = findEntityById(id);
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

    private MonthlyCourierQuotationDto toDtoWithEntries(MonthlyCourierQuotation entity) {
        List<MonthlyCourierEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(entity.getId());
        return toDto(entity, entries);
    }

    private MonthlyCourierQuotationDto toDto(MonthlyCourierQuotation entity, List<MonthlyCourierEntry> entries) {
        MonthlyCourierQuotationDto dto = new MonthlyCourierQuotationDto();
        dto.setId(entity.getId());
        dto.setShopId(entity.getShopId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setTitle(entity.getTitle());
        dto.setMonth(entity.getMonth());
        dto.setYear(entity.getYear());
        dto.setTotalShipments(entity.getTotalShipments());
        dto.setTotalWeight(entity.getTotalWeight());
        MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext ctx = calculationContext(entity);
        MonthlyCourierInvoiceGrandTotalService.PdfInvoiceTotals totals = (entries != null && !entries.isEmpty())
                ? invoiceGrandTotalService.computePdfTotals(ctx, entries)
                : null;
        double displayBase = totals != null
                ? totals.baseAmount()
                : (entity.getBaseAmount() != null ? entity.getBaseAmount() : 0.0);
        double displayTotal = totals != null
                ? totals.nettAmount()
                : (entity.getTotalAmount() != null ? entity.getTotalAmount() : 0.0);
        dto.setBaseAmount(displayBase);
        dto.setTotalAmount(displayTotal);
        if (totals != null) {
            dto.setFuelPercentage(totals.fuelPct());
            dto.setFuelAmount(totals.fuelAmount());
            dto.setFovPercentage(totals.fovPct());
            dto.setFovAmount(totals.fovAmount());
            dto.setSubTotal(totals.subTotal());
            dto.setDiscountAmount(totals.discountAmount());
            dto.setAdditionalCharges(totals.additionalCharges());
            dto.setTaxableAmount(totals.taxableAmount());
            dto.setGstAmount(totals.cgst() + totals.sgst());
        } else {
            dto.setFuelPercentage(invoiceGrandTotalService.resolveFuelPercentage(ctx));
            dto.setFuelAmount(0.0);
            dto.setFovPercentage(invoiceGrandTotalService.resolveFovPercentage(ctx));
            dto.setFovAmount(0.0);
            dto.setSubTotal(displayBase);
            dto.setDiscountAmount(entity.getDiscountAmount() != null ? entity.getDiscountAmount() : 0.0);
            dto.setAdditionalCharges(entity.getAdditionalCharges() != null ? entity.getAdditionalCharges() : 0.0);
            dto.setTaxableAmount(displayBase);
            dto.setGstAmount(0.0);
        }
        boolean isGen = Boolean.TRUE.equals(entity.getInvoiceGenerated()) ||
                (StringUtils.hasText(entity.getInvoiceNumber()));
        dto.setInvoiceGenerated(isGen);
        dto.setFuelChargePercentage(entity.getFuelChargePercentage());
        dto.setFovCharges(entity.getFovCharges());
        dto.setGstPercentage(entity.getGstPercentage());
        dto.setIncludeFuel(entity.getIncludeFuel());
        dto.setIncludeGst(entity.getIncludeGst());
        dto.setIncludeFov(entity.getIncludeFov());
        dto.setDiscountType(entity.getDiscountType());
        dto.setDiscountValue(entity.getDiscountValue());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setAdditionalCharges(entity.getAdditionalCharges());
        dto.setDiscountDescription(entity.getDiscountDescription());
        dto.setAdditionalChargesDescription(entity.getAdditionalChargesDescription());
        dto.setInvoiceNumber(entity.getInvoiceNumber());
        dto.setInvoiceDate(entity.getInvoiceDate());
        dto.setZone(entity.getZone());
        dto.setAmountStatus(entity.getAmountStatus() != null ? entity.getAmountStatus() : "Pending");
        dto.setDescription(entity.getDescription());
        dto.setIsDownloaded(entity.getIsDownloaded() != null ? entity.getIsDownloaded() : false);
        dto.setApplyQuotationRates(entity.getApplyQuotationRates());
        dto.setInvoiceActivityLogs(entity.getInvoiceActivityLogs() != null ? entity.getInvoiceActivityLogs() : new ArrayList<>());
        dto.setNote(entity.getNote());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        return dto;
    }
}

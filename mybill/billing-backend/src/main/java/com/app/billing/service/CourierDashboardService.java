package com.app.billing.service;

import com.app.billing.dao.CashBookingRepository;
import com.app.billing.dao.CollectionCenterEntryRepository;
import com.app.billing.dao.CollectionCustomerAwbRepository;
import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dao.MonthlyCourierQuotationRepository;
import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.dao.SmallClientEntryQuotationRepository;
import com.app.billing.dto.AccountingSummaryDto;
import com.app.billing.dto.CashFlowDailyDto;
import com.app.billing.dto.CourierDashboardDto;
import com.app.billing.model.CashBooking;
import com.app.billing.model.CollectionCenterEntry;
import com.app.billing.model.CollectionCustomerAwb;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.MonthlyCourierQuotation;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.model.SmallClientEntryQuotation;
import com.app.billing.security.DashboardCardKeys;
import com.app.billing.util.AmountStatusBucketUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourierDashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Inclusive date range; null from/to means open-ended on that side. */
    private static boolean isDateInRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    private final MonthlyCourierEntryRepository entryRepository;
    private final MonthlyCourierQuotationRepository quotationRepository;
    private final SmallClientEntryRepository smallClientEntryRepository;
    private final SmallClientEntryQuotationRepository smallClientEntryQuotationRepository;
    private final CollectionCenterEntryRepository collectionCenterEntryRepository;
    private final CollectionCustomerAwbRepository collectionCustomerAwbRepository;
    private final CashBookingRepository cashBookingRepository;
    private final AccountingService accountingService;
    private final MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;

    public CourierDashboardDto.FullPayload buildFull() {
        return buildFull(null, null, null, null, null, null, false);
    }

    /**
     * Build the dashboard payload, then strip any card/section the caller is not entitled to see.
     *
     * @param allowedCards set of permitted {@link DashboardCardKeys} keys; {@code null} means
     *                     unrestricted (e.g. ADMIN) and no filtering is applied.
     */
    public CourierDashboardDto.FullPayload buildFull(
            LocalDate revenueFrom,
            LocalDate revenueTo,
            LocalDate clientEntryFrom,
            LocalDate clientEntryTo,
            String clientEntryCourier,
            String clientEntryStatus,
            boolean revenueAll,
            Set<String> allowedCards) {
        CourierDashboardDto.FullPayload payload = buildFull(revenueFrom, revenueTo, clientEntryFrom,
                clientEntryTo, clientEntryCourier, clientEntryStatus, revenueAll);
        applyCardAccess(payload, allowedCards);
        return payload;
    }

    /**
     * @param revenueFrom optional inclusive start for revenue cards + client/collection charts
     * @param revenueTo     optional inclusive end (both required together to activate range mode)
     */
    public CourierDashboardDto.FullPayload buildFull(LocalDate revenueFrom, LocalDate revenueTo) {
        return buildFull(revenueFrom, revenueTo, null, null, null, null, false);
    }

    public CourierDashboardDto.FullPayload buildFull(
            LocalDate revenueFrom,
            LocalDate revenueTo,
            LocalDate clientEntryFrom,
            LocalDate clientEntryTo,
            String clientEntryCourier,
            String clientEntryStatus) {
        return buildFull(revenueFrom, revenueTo, clientEntryFrom, clientEntryTo, clientEntryCourier,
                clientEntryStatus, false);
    }

    public CourierDashboardDto.FullPayload buildFull(
            LocalDate revenueFrom,
            LocalDate revenueTo,
            LocalDate clientEntryFrom,
            LocalDate clientEntryTo,
            String clientEntryCourier,
            String clientEntryStatus,
            boolean revenueAll) {
        List<MonthlyCourierEntry> all = entryRepository.findAll();
        Map<String, MonthlyCourierQuotation> quotationsById = loadQuotationsById();
        Map<String, String> quotationIdToClient = buildQuotationClientNames(quotationsById);

        LocalDate today = LocalDate.now(IST);
        YearMonth ym = YearMonth.now(IST);
        LocalDate monthStart = ym.atDay(1);

        LocalDate rfRaw = revenueFrom != null ? revenueFrom : clientEntryFrom;
        LocalDate rtRaw = revenueTo != null ? revenueTo : clientEntryTo;
        boolean filterActive = revenueAll || rfRaw != null || rtRaw != null
                || StringUtils.hasText(clientEntryCourier) || StringUtils.hasText(clientEntryStatus);
        if (!filterActive) {
            return buildEmptyDashboardPayload();
        }

        boolean rangeActive = !revenueAll && (rfRaw != null || rtRaw != null);
        final LocalDate rangeFrom;
        final LocalDate rangeTo;
        if (rangeActive && rfRaw != null && rtRaw != null && rfRaw.isAfter(rtRaw)) {
            rangeFrom = rtRaw;
            rangeTo = rfRaw;
        } else if (rangeActive) {
            rangeFrom = rfRaw;
            rangeTo = rtRaw;
        } else {
            rangeFrom = null;
            rangeTo = null;
        }
        List<MonthlyCourierEntry> scopedEntries = rangeActive
                ? all.stream().filter(e -> isDateInRange(e.getEntryDate(), rangeFrom, rangeTo))
                .collect(Collectors.toList())
                : all;

        boolean clientEntryFilterActive = rangeActive || revenueAll
                || StringUtils.hasText(clientEntryCourier) || StringUtils.hasText(clientEntryStatus);
        LocalDate filterDateFrom = rangeActive ? rangeFrom : null;
        LocalDate filterDateTo = rangeActive ? rangeTo : null;
        List<MonthlyCourierEntry> clientEntryFiltered = filterClientEntryRows(
                all, filterDateFrom, filterDateTo, clientEntryCourier, clientEntryStatus);

        List<MonthlyCourierEntry> bookingsTodayScope = clientEntryFilterActive
                ? clientEntryFiltered
                : (rangeActive ? scopedEntries : all);
        long bookingsToday = bookingsTodayScope.stream().filter(e -> today.equals(e.getEntryDate())).count();
        long clientBookingsCount = clientEntryFilterActive
                ? clientEntryFiltered.size()
                : (rangeActive ? scopedEntries.size() : all.size());

        List<CollectionCenterEntry> collAll = collectionCenterEntryRepository.findAll();
        List<CollectionCenterEntry> collFiltered = filterCollectionCenterRows(
                collAll, filterDateFrom, filterDateTo, clientEntryCourier, clientEntryStatus);

        long delivered = 0, inTransit = 0, pending = 0, cancelled = 0, failed = 0;
        List<MonthlyCourierEntry> statusScope = clientEntryFilterActive
                ? clientEntryFiltered
                : (rangeActive ? scopedEntries : all);
        for (MonthlyCourierEntry e : statusScope) {
            String st = normalizeStatus(e.getDeliveryStatus());
            switch (st) {
                case "DELIVERED" -> delivered++;
                case "IN_TRANSIT" -> inTransit++;
                case "CANCELLED" -> cancelled++;
                case "FAILED" -> failed++;
                default -> pending++;
            }
        }

        BigDecimal totalRev;
        BigDecimal monthRev;
        BigDecimal codPending = BigDecimal.ZERO;

        List<MonthlyCourierEntry> clientRevScope = clientEntryFilterActive
                ? clientEntryFiltered
                : (rangeActive ? scopedEntries : all);
        if (rangeActive || clientEntryFilterActive) {
            totalRev = computeClientRevenueBreakdown(clientRevScope, quotationsById).totalRevenue();
            for (MonthlyCourierEntry e : clientRevScope) {
                double amt = e.getAmount() != null ? e.getAmount() : 0;
                String raw = e.getDeliveryStatus() != null ? e.getDeliveryStatus() : "";
                if (raw.toUpperCase().contains("COD") && raw.toUpperCase().contains("PEND")) {
                    codPending = codPending.add(BigDecimal.valueOf(amt));
                }
            }
            monthRev = collFiltered.stream()
                    .filter(e -> e.getAmount() != null)
                    .map(e -> BigDecimal.valueOf(e.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            totalRev = computeClientRevenueBreakdown(all, quotationsById).totalRevenue();
            monthRev = BigDecimal.ZERO;
            for (MonthlyCourierEntry e : all) {
                double amt = e.getAmount() != null ? e.getAmount() : 0;
                if (e.getEntryDate() != null && !e.getEntryDate().isBefore(monthStart) && !e.getEntryDate().isAfter(today)) {
                    monthRev = monthRev.add(BigDecimal.valueOf(amt));
                }
                String raw = e.getDeliveryStatus() != null ? e.getDeliveryStatus() : "";
                if (raw.toUpperCase().contains("COD") && raw.toUpperCase().contains("PEND")) {
                    codPending = codPending.add(BigDecimal.valueOf(amt));
                }
            }
        }

        List<CashBooking> cashAll = cashBookingRepository.findAll();
        List<CashBooking> cashFiltered = filterCashBookingRows(
                cashAll, filterDateFrom, filterDateTo, clientEntryCourier, clientEntryStatus);

        List<SmallClientEntry> smallAll = smallClientEntryRepository.findAll();
        Map<String, SmallClientEntryQuotation> smallQuotationsById = smallClientEntryQuotationRepository.findAll().stream()
                .collect(Collectors.toMap(SmallClientEntryQuotation::getId, q -> q, (a, b) -> a));
        Map<String, String> smallQuotationIdToClient = smallQuotationsById.values().stream()
                .collect(Collectors.toMap(SmallClientEntryQuotation::getId,
                        q -> q.getCustomerName() != null ? q.getCustomerName() : "Unknown", (a, b) -> a));
        List<SmallClientEntry> smallFiltered = filterSmallClientEntryRows(
                smallAll, filterDateFrom, filterDateTo, clientEntryCourier, clientEntryStatus);

        long shipmentsAllModulesCount = (long) clientEntryFiltered.size()
                + collFiltered.size() + cashFiltered.size() + smallFiltered.size();

        long cashCount = cashFiltered.size();
        BigDecimal cashRev = BigDecimal.ZERO;
        long cashDelivered = 0;
        for (CashBooking c : cashFiltered) {
            if (c.getAmount() != null) {
                cashRev = cashRev.add(BigDecimal.valueOf(c.getAmount()));
            }
            if ("DELIVERED".equals(normalizeCashBookingStatus(c.getStatus()))) {
                cashDelivered++;
            }
        }

        CourierDashboardDto.Summary summary = CourierDashboardDto.Summary.builder()
                .bookingsToday(bookingsToday)
                .clientBookingsCount(clientBookingsCount)
                .shipmentsAllModulesCount(shipmentsAllModulesCount)
                .delivered(delivered)
                .inTransit(inTransit)
                .pending(pending)
                .cancelled(cancelled)
                .failedDeliveries(failed)
                .totalRevenue(totalRev)
                .monthlyRevenue(monthRev)
                .codPending(codPending)
                .cashBookingsCount(cashCount)
                .cashBookingRevenue(cashRev)
                .deliveredCashBookings(cashDelivered)
                .revenueRangeActive(rangeActive || revenueAll)
                .build();

        List<MonthlyCourierEntry> chartEntries = clientEntryFilterActive
                ? clientEntryFiltered
                : (rangeActive ? scopedEntries : all);
        List<CourierDashboardDto.DailyPoint> dailyTrend = buildDailyBookingTrend(chartEntries, today, rangeFrom, rangeTo, rangeActive);

        Map<String, BigDecimal> monthBuckets = new TreeMap<>();
        for (MonthlyCourierEntry e : chartEntries) {
            if (e.getEntryDate() == null || e.getAmount() == null) {
                continue;
            }
            String key = e.getEntryDate().getYear() + "-" + String.format("%02d", e.getEntryDate().getMonthValue());
            monthBuckets.merge(key, BigDecimal.valueOf(e.getAmount()), BigDecimal::add);
        }
        List<CourierDashboardDto.MonthlyRevenuePoint> monthTrend = monthBuckets.entrySet().stream()
                .map(en -> CourierDashboardDto.MonthlyRevenuePoint.builder().monthKey(en.getKey()).revenue(en.getValue()).build())
                .collect(Collectors.toList());

        Map<String, Long> statusCounts = chartEntries.stream()
                .collect(Collectors.groupingBy(e -> normalizeStatus(e.getDeliveryStatus()), Collectors.counting()));
        List<CourierDashboardDto.StatusSlice> slices = statusCounts.entrySet().stream()
                .map(en -> CourierDashboardDto.StatusSlice.builder().status(en.getKey()).count(en.getValue()).build())
                .collect(Collectors.toList());

        Map<String, BigDecimal> clientRev = new HashMap<>();
        for (MonthlyCourierEntry e : chartEntries) {
            if (e.getAmount() == null) {
                continue;
            }
            String qid = e.getMonthlyQuotationId();
            String name = quotationIdToClient.getOrDefault(qid, "Unknown");
            clientRev.merge(name, BigDecimal.valueOf(e.getAmount()), BigDecimal::add);
        }
        List<CourierDashboardDto.ClientRevenue> topClients = clientRev.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(8)
                .map(en -> CourierDashboardDto.ClientRevenue.builder().clientName(en.getKey()).revenue(en.getValue()).build())
                .collect(Collectors.toList());

        List<CourierDashboardDto.ActivityRow> latestDelivered = chartEntries.stream()
                .filter(e -> "DELIVERED".equals(normalizeStatus(e.getDeliveryStatus())))
                .sorted(Comparator.comparing(MonthlyCourierEntry::getEntryDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(10)
                .map(e -> toActivity(e, quotationIdToClient))
                .collect(Collectors.toList());

        List<CourierDashboardDto.ActivityRow> failedRows = chartEntries.stream()
                .filter(e -> "FAILED".equals(normalizeStatus(e.getDeliveryStatus())))
                .sorted(Comparator.comparing(MonthlyCourierEntry::getEntryDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(10)
                .map(e -> toActivity(e, quotationIdToClient))
                .collect(Collectors.toList());

        long pendingAwbs = collectionCustomerAwbRepository.countByStatus(CollectionCustomerAwb.STATUS_PENDING);
        AccountingSummaryDto accSummary = accountingService.summaryInRange(filterDateFrom, filterDateTo);
        List<CashFlowDailyDto> cashFlow = rangeActive
                ? accountingService.cashFlowTrendInRange(rangeFrom, rangeTo)
                : accountingService.cashFlowTrendLastDays(7);

        CourierDashboardDto.ModuleRangeKpi collectionCenterKpi = buildCollectionCenterKpi(
                collFiltered, filterDateFrom, filterDateTo);
        CourierDashboardDto.ModuleRangeKpi cashBookingKpi = buildCashBookingKpi(
                cashFiltered, filterDateFrom, filterDateTo);
        CourierDashboardDto.ModuleRangeKpi clientEntryKpi = buildClientEntryKpi(
                clientEntryFiltered, quotationsById, filterDateFrom, filterDateTo);
        List<CourierDashboardDto.ModuleAwbGroup> clientEntryAwbGroups =
                buildClientEntryAwbGroups(clientEntryFiltered, quotationIdToClient);
        List<CourierDashboardDto.ModuleAwbGroup> collectionCenterAwbGroups = buildCollectionAwbGroups(collFiltered);
        List<CourierDashboardDto.ModuleAwbGroup> cashBookingAwbGroups = buildCashBookingAwbGroups(cashFiltered);

        CourierDashboardDto.ModuleRangeKpi smallClientEntryKpi = buildSmallClientEntryKpi(
                smallFiltered, smallQuotationsById, filterDateFrom, filterDateTo);
        List<CourierDashboardDto.ModuleAwbGroup> smallClientEntryAwbGroups =
                buildSmallClientEntryAwbGroups(smallFiltered, smallQuotationIdToClient);

        return CourierDashboardDto.FullPayload.builder()
                .summary(summary)
                .revenueFrom(rangeActive ? rangeFrom : null)
                .revenueTo(rangeActive ? rangeTo : null)
                .dailyBookingTrend(dailyTrend)
                .monthlyRevenueTrend(monthTrend)
                .statusDistribution(slices)
                .topClientsByRevenue(topClients)
                .latestDelivered(latestDelivered)
                .failedDeliveries(failedRows)
                .collection(buildCollectionDashboard(collFiltered, filterDateFrom, filterDateTo))
                .pendingCollectionAwbs(pendingAwbs)
                .accounting(accSummary)
                .cashFlowTrend(cashFlow)
                .collectionCenterKpi(collectionCenterKpi)
                .cashBookingKpi(cashBookingKpi)
                .clientEntryKpi(clientEntryKpi)
                .clientEntryAwbGroups(clientEntryAwbGroups)
                .collectionCenterAwbGroups(collectionCenterAwbGroups)
                .cashBookingAwbGroups(cashBookingAwbGroups)
                .smallClientEntryKpi(smallClientEntryKpi)
                .smallClientEntryAwbGroups(smallClientEntryAwbGroups)
                .build();
    }

    private static List<CollectionCenterEntry> filterCollectionCenterRows(
            List<CollectionCenterEntry> rows,
            LocalDate from,
            LocalDate to,
            String courier,
            String status) {
        return rows.stream()
                .filter(e -> {
                    if (!isDateInRange(e.getEntryDate(), from, to)) {
                        return false;
                    }
                    if (StringUtils.hasText(courier)) {
                        String ct = e.getCourier() != null ? e.getCourier() : "";
                        if (!ct.toLowerCase().contains(courier.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    if (StringUtils.hasText(status)) {
                        String st = e.getStatus() != null ? e.getStatus() : "";
                        if (!st.toLowerCase().contains(status.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private static List<CashBooking> filterCashBookingRows(
            List<CashBooking> rows,
            LocalDate from,
            LocalDate to,
            String courier,
            String status) {
        return rows.stream()
                .filter(e -> {
                    if (!isDateInRange(e.getBookingDate(), from, to)) {
                        return false;
                    }
                    if (StringUtils.hasText(courier)) {
                        String ct = e.getCourier() != null ? e.getCourier() : "";
                        if (!ct.toLowerCase().contains(courier.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    if (StringUtils.hasText(status)) {
                        String st = e.getStatus() != null ? e.getStatus() : "";
                        if (!st.toLowerCase().contains(status.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private static List<SmallClientEntry> filterSmallClientEntryRows(
            List<SmallClientEntry> rows,
            LocalDate from,
            LocalDate to,
            String courier,
            String status) {
        return rows.stream()
                .filter(e -> {
                    if (from != null && (e.getEntryDate() == null || e.getEntryDate().isBefore(from))) {
                        return false;
                    }
                    if (to != null && (e.getEntryDate() == null || e.getEntryDate().isAfter(to))) {
                        return false;
                    }
                    if (StringUtils.hasText(courier)) {
                        String ct = e.getCourierType() != null ? e.getCourierType() : "";
                        if (!ct.toLowerCase().contains(courier.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    if (StringUtils.hasText(status)) {
                        String st = e.getDeliveryStatus() != null ? e.getDeliveryStatus() : "";
                        if (!st.toLowerCase().contains(status.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private static List<CourierDashboardDto.ModuleAwbGroup> buildSmallClientEntryAwbGroups(
            List<SmallClientEntry> rows, Map<String, String> quotationIdToClient) {
        Map<String, Long> counts = new HashMap<>();
        for (SmallClientEntry e : rows) {
            String name = quotationIdToClient.getOrDefault(e.getMonthlyQuotationId(), "Unknown");
            counts.merge(name, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(en -> CourierDashboardDto.ModuleAwbGroup.builder()
                        .name(en.getKey())
                        .totalCount(en.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private CourierDashboardDto.ModuleRangeKpi buildSmallClientEntryKpi(
            List<SmallClientEntry> rows,
            Map<String, SmallClientEntryQuotation> quotationsById,
            LocalDate from,
            LocalDate to) {
        Map<String, MonthlyCourierQuotation> castQuotations = quotationsById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    SmallClientEntryQuotation sq = e.getValue();
                    MonthlyCourierQuotation mq = new MonthlyCourierQuotation();
                    mq.setId(sq.getId());
                    mq.setCustomerId(sq.getCustomerId());
                    mq.setCustomerName(sq.getCustomerName());
                    mq.setZone(sq.getZone());
                    mq.setFuelChargePercentage(sq.getFuelChargePercentage());
                    mq.setFovCharges(sq.getFovCharges());
                    mq.setGstPercentage(sq.getGstPercentage());
                    mq.setIncludeFuel(sq.getIncludeFuel());
                    mq.setIncludeGst(sq.getIncludeGst());
                    mq.setIncludeFov(sq.getIncludeFov());
                    return mq;
                }));
        List<MonthlyCourierEntry> castRows = rows.stream().map(r -> {
            MonthlyCourierEntry m = new MonthlyCourierEntry();
            m.setId(r.getId());
            m.setMonthlyQuotationId(r.getMonthlyQuotationId());
            m.setEntryDate(r.getEntryDate());
            m.setAmount(r.getAmount());
            m.setAmountStatus(r.getAmountStatus());
            m.setDeliveryStatus(r.getDeliveryStatus());
            m.setCourierType(r.getCourierType());
            return m;
        }).collect(Collectors.toList());
        return buildClientEntryKpi(castRows, castQuotations, from, to);
    }

    private static List<MonthlyCourierEntry> filterClientEntryRows(
            List<MonthlyCourierEntry> rows,
            LocalDate from,
            LocalDate to,
            String courier,
            String status) {
        return rows.stream()
                .filter(e -> {
                    if (from != null && (e.getEntryDate() == null || e.getEntryDate().isBefore(from))) {
                        return false;
                    }
                    if (to != null && (e.getEntryDate() == null || e.getEntryDate().isAfter(to))) {
                        return false;
                    }
                    if (StringUtils.hasText(courier)) {
                        String ct = e.getCourierType() != null ? e.getCourierType() : "";
                        if (!ct.toLowerCase().contains(courier.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    if (StringUtils.hasText(status)) {
                        String st = e.getDeliveryStatus() != null ? e.getDeliveryStatus() : "";
                        if (!st.toLowerCase().contains(status.trim().toLowerCase())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private static List<CourierDashboardDto.ModuleAwbGroup> buildClientEntryAwbGroups(
            List<MonthlyCourierEntry> rows, Map<String, String> quotationIdToClient) {
        Map<String, Long> counts = new HashMap<>();
        for (MonthlyCourierEntry e : rows) {
            String name = quotationIdToClient.getOrDefault(e.getMonthlyQuotationId(), "Unknown");
            counts.merge(name, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(en -> CourierDashboardDto.ModuleAwbGroup.builder()
                        .name(en.getKey())
                        .totalCount(en.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<CourierDashboardDto.ModuleAwbGroup> buildCollectionAwbGroups(List<CollectionCenterEntry> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (CollectionCenterEntry e : rows) {
            String name = e.getCustomerName() != null && !e.getCustomerName().isBlank()
                    ? e.getCustomerName().trim()
                    : "Unknown";
            counts.merge(name, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(en -> CourierDashboardDto.ModuleAwbGroup.builder()
                        .name(en.getKey())
                        .totalCount(en.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<CourierDashboardDto.ModuleAwbGroup> buildCashBookingAwbGroups(List<CashBooking> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (CashBooking e : rows) {
            String name = e.getReceiverName() != null && !e.getReceiverName().isBlank()
                    ? e.getReceiverName().trim()
                    : "Direct booking";
            counts.merge(name, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(en -> CourierDashboardDto.ModuleAwbGroup.builder()
                        .name(en.getKey())
                        .totalCount(en.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private static CourierDashboardDto.ModuleRangeKpi buildCollectionCenterKpi(
            List<CollectionCenterEntry> rows, LocalDate from, LocalDate to) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal gpay = BigDecimal.ZERO;
        BigDecimal pend = BigDecimal.ZERO;
        BigDecimal cod = BigDecimal.ZERO;
        for (CollectionCenterEntry e : rows) {
            double amt = e.getAmount() != null ? e.getAmount() : 0;
            BigDecimal a = BigDecimal.valueOf(amt);
            switch (AmountStatusBucketUtil.classify(e.getAmountStatus())) {
                case CASH -> cash = cash.add(a);
                case GPAY -> gpay = gpay.add(a);
                case PENDING -> pend = pend.add(a);
                case COD -> cod = cod.add(a);
                default -> {
                }
            }
        }
        return CourierDashboardDto.ModuleRangeKpi.builder()
                .from(from)
                .to(to)
                .totalBookings((long) rows.size())
                .cashAmount(cash)
                .gpayAmount(gpay)
                .pendingAmount(pend)
                .codAmount(cod)
                .revenueTotal(null)
                .build();
    }

    private static CourierDashboardDto.ModuleRangeKpi buildCashBookingKpi(List<CashBooking> rows, LocalDate from, LocalDate to) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal gpay = BigDecimal.ZERO;
        BigDecimal pend = BigDecimal.ZERO;
        BigDecimal cod = BigDecimal.ZERO;
        for (CashBooking e : rows) {
            double amt = e.getAmount() != null ? e.getAmount() : 0;
            BigDecimal a = BigDecimal.valueOf(amt);
            switch (AmountStatusBucketUtil.classify(e.getAmountStatus())) {
                case CASH -> cash = cash.add(a);
                case GPAY -> gpay = gpay.add(a);
                case PENDING -> pend = pend.add(a);
                case COD -> cod = cod.add(a);
                default -> {
                }
            }
        }
        return CourierDashboardDto.ModuleRangeKpi.builder()
                .from(from)
                .to(to)
                .totalBookings((long) rows.size())
                .cashAmount(cash)
                .gpayAmount(gpay)
                .pendingAmount(pend)
                .codAmount(cod)
                .revenueTotal(null)
                .build();
    }

    private CourierDashboardDto.ModuleRangeKpi buildClientEntryKpi(
            List<MonthlyCourierEntry> rows,
            Map<String, MonthlyCourierQuotation> quotationsById,
            LocalDate from,
            LocalDate to) {
        MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown revenue =
                computeClientRevenueBreakdown(rows, quotationsById);
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal gpay = BigDecimal.ZERO;
        BigDecimal pend = BigDecimal.ZERO;
        BigDecimal cod = BigDecimal.ZERO;
        for (MonthlyCourierEntry e : rows) {
            double amt = e.getAmount() != null ? e.getAmount() : 0;
            BigDecimal a = BigDecimal.valueOf(amt);
            switch (AmountStatusBucketUtil.classify(e.getAmountStatus())) {
                case CASH -> cash = cash.add(a);
                case GPAY -> gpay = gpay.add(a);
                case PENDING -> pend = pend.add(a);
                case COD -> cod = cod.add(a);
                default -> {
                }
            }
        }
        return CourierDashboardDto.ModuleRangeKpi.builder()
                .from(from)
                .to(to)
                .totalBookings((long) rows.size())
                .cashAmount(cash)
                .gpayAmount(gpay)
                .pendingAmount(pend)
                .codAmount(cod)
                .revenueTotal(revenue.totalRevenue())
                .baseRevenue(revenue.baseRevenue())
                .fuelCharges(revenue.fuelCharges())
                .gstAmount(revenue.gstAmount())
                .fovAmount(revenue.fovAmount())
                .build();
    }

    private MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown computeClientRevenueBreakdown(
            List<MonthlyCourierEntry> rows,
            Map<String, MonthlyCourierQuotation> quotationsById) {
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal fuel = BigDecimal.ZERO;
        BigDecimal gst = BigDecimal.ZERO;
        BigDecimal fov = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, List<MonthlyCourierEntry>> byQuotation = rows.stream()
                .filter(e -> e.getMonthlyQuotationId() != null)
                .collect(Collectors.groupingBy(MonthlyCourierEntry::getMonthlyQuotationId));
        for (Map.Entry<String, List<MonthlyCourierEntry>> en : byQuotation.entrySet()) {
            MonthlyCourierQuotation quotation = quotationsById.get(en.getKey());
            MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext ctx = quotation != null
                    ? MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                            quotation.getCustomerId(),
                            quotation.getFuelChargePercentage(),
                            quotation.getFovCharges(),
                            quotation.getGstPercentage(),
                            quotation.getIncludeFuel(),
                            quotation.getIncludeGst(),
                            quotation.getIncludeFov())
                    : MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.defaults(null);
            MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown b =
                    invoiceGrandTotalService.computeInvoiceBreakdown(ctx, en.getValue());
            base = base.add(b.baseRevenue());
            fuel = fuel.add(b.fuelCharges());
            gst = gst.add(b.gstAmount());
            fov = fov.add(b.fovAmount());
            total = total.add(b.totalRevenue());
        }
        List<MonthlyCourierEntry> withoutQuotation = rows.stream()
                .filter(e -> e.getMonthlyQuotationId() == null)
                .collect(Collectors.toList());
        if (!withoutQuotation.isEmpty()) {
            MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown b =
                    invoiceGrandTotalService.computeInvoiceBreakdown(
                            MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.defaults(null),
                            withoutQuotation);
            base = base.add(b.baseRevenue());
            fuel = fuel.add(b.fuelCharges());
            gst = gst.add(b.gstAmount());
            fov = fov.add(b.fovAmount());
            total = total.add(b.totalRevenue());
        }
        return new MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown(base, fuel, gst, fov, total);
    }

    private List<CourierDashboardDto.DailyPoint> buildDailyBookingTrend(
            List<MonthlyCourierEntry> source,
            LocalDate today,
            LocalDate rf,
            LocalDate rt,
            boolean rangeActive) {
        if (rangeActive && rf != null && rt != null) {
            LocalDate from = rf;
            LocalDate to = rt;
            long span = ChronoUnit.DAYS.between(from, to) + 1;
            if (span > 120) {
                from = to.minusDays(119);
            }
            List<CourierDashboardDto.DailyPoint> out = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                final LocalDate day = d;
                long c = source.stream().filter(e -> day.equals(e.getEntryDate())).count();
                out.add(CourierDashboardDto.DailyPoint.builder().date(day).count(c).build());
            }
            return out;
        }
        List<CourierDashboardDto.DailyPoint> dailyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long c = source.stream().filter(e -> d.equals(e.getEntryDate())).count();
            dailyTrend.add(CourierDashboardDto.DailyPoint.builder().date(d).count(c).build());
        }
        return dailyTrend;
    }

    private CourierDashboardDto.CollectionDashboard buildCollectionDashboard(
            List<CollectionCenterEntry> rows, LocalDate rf, LocalDate rt) {

        long paid = 0, pending = 0, cod = 0;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (CollectionCenterEntry e : rows) {
            double a = e.getAmount() != null ? e.getAmount() : 0;
            totalAmt = totalAmt.add(BigDecimal.valueOf(a));
            String ast = e.getAmountStatus() != null ? e.getAmountStatus().trim() : "";
            if (ast.equalsIgnoreCase("Paid")) {
                paid++;
            } else if (ast.equalsIgnoreCase("Pending") || ast.equalsIgnoreCase("UnPaid")) {
                pending++;
            } else if (ast.equalsIgnoreCase("CashOnDelivery")) {
                cod++;
            }
        }
        CourierDashboardDto.CollectionSummary csum = CourierDashboardDto.CollectionSummary.builder()
                .totalEntries(rows.size())
                .paidCollections(paid)
                .pendingCollections(pending)
                .codCollections(cod)
                .totalCollectionAmount(totalAmt)
                .build();

        LocalDate today = LocalDate.now();
        List<CourierDashboardDto.CollectionDailyPoint> dailyCol = new ArrayList<>();
        if (rf != null && rt != null) {
            LocalDate from = rf;
            LocalDate to = rt;
            long span = ChronoUnit.DAYS.between(from, to) + 1;
            if (span > 120) {
                from = to.minusDays(119);
            }
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                final LocalDate day = d;
                long c = rows.stream().filter(e -> day.equals(e.getEntryDate())).count();
                dailyCol.add(CourierDashboardDto.CollectionDailyPoint.builder().date(day).count(c).build());
            }
        } else {
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                long c = rows.stream().filter(e -> d.equals(e.getEntryDate())).count();
                dailyCol.add(CourierDashboardDto.CollectionDailyPoint.builder().date(d).count(c).build());
            }
        }

        Map<String, Long> amDist = rows.stream()
                .collect(Collectors.groupingBy(e -> e.getAmountStatus() != null && !e.getAmountStatus().isBlank()
                        ? e.getAmountStatus() : "(none)", Collectors.counting()));
        List<CourierDashboardDto.CollectionAmountSlice> slices = amDist.entrySet().stream()
                .map(en -> CourierDashboardDto.CollectionAmountSlice.builder().amountStatus(en.getKey()).count(en.getValue()).build())
                .collect(Collectors.toList());

        Map<String, BigDecimal> custAmt = new HashMap<>();
        for (CollectionCenterEntry e : rows) {
            if (e.getAmount() == null) {
                continue;
            }
            String n = e.getCustomerName() != null ? e.getCustomerName() : "Unknown";
            custAmt.merge(n, BigDecimal.valueOf(e.getAmount()), BigDecimal::add);
        }
        List<CourierDashboardDto.CollectionCustomerTop> topC = custAmt.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(8)
                .map(en -> CourierDashboardDto.CollectionCustomerTop.builder().customerName(en.getKey()).amount(en.getValue()).build())
                .collect(Collectors.toList());

        Map<String, Long> monthBucketsCol = new TreeMap<>();
        for (CollectionCenterEntry e : rows) {
            String k;
            if (e.getEntryYear() != null && StringUtils.hasText(e.getEntryMonth())) {
                k = e.getEntryYear() + " " + e.getEntryMonth();
            } else if (e.getEntryDate() != null) {
                k = e.getEntryDate().getYear() + "-" + String.format("%02d", e.getEntryDate().getMonthValue());
            } else {
                continue;
            }
            monthBucketsCol.merge(k, 1L, Long::sum);
        }
        List<CourierDashboardDto.CollectionMonthTrend> monthTrendCol = monthBucketsCol.entrySet().stream()
                .map(en -> CourierDashboardDto.CollectionMonthTrend.builder().monthKey(en.getKey()).count(en.getValue()).build())
                .collect(Collectors.toList());

        return CourierDashboardDto.CollectionDashboard.builder()
                .summary(csum)
                .dailyCollectionTrend(dailyCol)
                .amountStatusDistribution(slices)
                .topCollectionCustomers(topC)
                .monthlyCollectionTrend(monthTrendCol)
                .build();
    }

    private Map<String, MonthlyCourierQuotation> loadQuotationsById() {
        return quotationRepository.findAll().stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(MonthlyCourierQuotation::getId, q -> q, (a, b) -> a));
    }

    private Map<String, String> buildQuotationClientNames(Map<String, MonthlyCourierQuotation> quotationsById) {
        Map<String, String> map = new HashMap<>();
        for (MonthlyCourierQuotation q : quotationsById.values()) {
            String name = q.getCustomerName() != null ? q.getCustomerName() : "";
            map.put(q.getId(), name.isEmpty() ? "Unknown" : name);
        }
        return map;
    }

    private static CourierDashboardDto.ActivityRow toActivity(MonthlyCourierEntry e, Map<String, String> quotationIdToClient) {
        String client = quotationIdToClient.getOrDefault(e.getMonthlyQuotationId(), "");
        String recv = e.getReceiverName() != null ? e.getReceiverName()
                : (e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "");
        return CourierDashboardDto.ActivityRow.builder()
                .awb(e.getTrackingNumber())
                .clientName(client)
                .receiver(recv)
                .status(e.getDeliveryStatus())
                .entryDate(e.getEntryDate())
                .amount(e.getAmount())
                .build();
    }

    private static String normalizeStatus(String s) {
        if (s == null || s.isBlank()) {
            return "PENDING";
        }
        String u = s.toUpperCase();
        if (u.contains("DELIVER")) {
            return "DELIVERED";
        }
        if (u.contains("TRANSIT") || u.contains("TRANS")) {
            return "IN_TRANSIT";
        }
        if (u.contains("CANCEL")) {
            return "CANCELLED";
        }
        if (u.contains("RTO") || u.contains("FAIL")) {
            return "FAILED";
        }
        return "PENDING";
    }

    /** Align with client-entry style status keywords for cash booking rows. */
    private static String normalizeCashBookingStatus(String s) {
        return normalizeStatus(s);
    }

    /**
     * Null out every card/section the user is not entitled to, so restricted data never leaves the API.
     * {@code allowed == null} means unrestricted (ADMIN) and the payload is returned untouched.
     */
    private void applyCardAccess(CourierDashboardDto.FullPayload payload, Set<String> allowed) {
        if (payload == null || allowed == null) {
            return;
        }

        // Section 1 - Revenue & Shipments
        CourierDashboardDto.Summary s = payload.getSummary();
        if (s != null) {
            if (!allowed.contains(DashboardCardKeys.VIEW_CLIENT_BOOKINGS_RANGE)) {
                s.setClientBookingsCount(null);
                s.setBookingsToday(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_SHIPMENTS_ALL_MODULES)) {
                s.setShipmentsAllModulesCount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_DELIVERED)) {
                s.setDelivered(null);
                s.setFailedDeliveries(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_IN_TRANSIT)) {
                s.setInTransit(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_PENDING)) {
                s.setPending(null);
                s.setCancelled(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CLIENT_REVENUE)) {
                s.setTotalRevenue(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_COD_PENDING)) {
                s.setCodPending(null);
            }
        }

        // Section 2 - Collection Center
        if (payload.getCollectionCenterKpi() != null) {
            CourierDashboardDto.ModuleRangeKpi k = payload.getCollectionCenterKpi();
            if (!allowed.contains(DashboardCardKeys.VIEW_CC_TOTAL_BOOKINGS)) {
                k.setTotalBookings(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CC_CASH)) {
                k.setCashAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CC_GPAY)) {
                k.setGpayAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CC_PENDING)) {
                k.setPendingAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CC_COD)) {
                k.setCodAmount(null);
            }
            if (isModuleKpiFullyHidden(allowed, DashboardCardKeys.VIEW_CC_TOTAL_BOOKINGS,
                    DashboardCardKeys.VIEW_CC_CASH, DashboardCardKeys.VIEW_CC_GPAY,
                    DashboardCardKeys.VIEW_CC_PENDING, DashboardCardKeys.VIEW_CC_COD,
                    DashboardCardKeys.VIEW_CC_TOTAL_AMOUNT)) {
                payload.setCollectionCenterKpi(null);
            }
        }

        // Section 3 - Cash Booking
        if (payload.getCashBookingKpi() != null) {
            CourierDashboardDto.ModuleRangeKpi k = payload.getCashBookingKpi();
            if (!allowed.contains(DashboardCardKeys.VIEW_CB_TOTAL_BOOKINGS)) {
                k.setTotalBookings(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CB_CASH_AMOUNT)) {
                k.setCashAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CB_ONLINE_AMOUNT)) {
                k.setGpayAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CB_PENDING)) {
                k.setPendingAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CB_COD)) {
                k.setCodAmount(null);
            }
            if (isModuleKpiFullyHidden(allowed, DashboardCardKeys.VIEW_CB_TOTAL_BOOKINGS,
                    DashboardCardKeys.VIEW_CB_CASH_AMOUNT, DashboardCardKeys.VIEW_CB_ONLINE_AMOUNT,
                    DashboardCardKeys.VIEW_CB_PENDING, DashboardCardKeys.VIEW_CB_COD,
                    DashboardCardKeys.VIEW_CB_TOTAL_AMOUNT)) {
                payload.setCashBookingKpi(null);
            }
        }

        // Section 4 - Client Entry
        if (payload.getClientEntryKpi() != null) {
            CourierDashboardDto.ModuleRangeKpi k = payload.getClientEntryKpi();
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_TOTAL_BOOKINGS)) {
                k.setTotalBookings(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_CASH)) {
                k.setCashAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_GPAY)) {
                k.setGpayAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_PENDING)) {
                k.setPendingAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_COD)) {
                k.setCodAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_BASE_REVENUE)) {
                k.setBaseRevenue(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_FUEL_CHARGES)) {
                k.setFuelCharges(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_GST_AMOUNT)) {
                k.setGstAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CE_TOTAL_REVENUE)) {
                k.setRevenueTotal(null);
                k.setFovAmount(null);
            }
            if (isModuleKpiFullyHidden(allowed, DashboardCardKeys.VIEW_CE_TOTAL_BOOKINGS,
                    DashboardCardKeys.VIEW_CE_CASH, DashboardCardKeys.VIEW_CE_GPAY,
                    DashboardCardKeys.VIEW_CE_PENDING, DashboardCardKeys.VIEW_CE_COD,
                    DashboardCardKeys.VIEW_CE_BASE_REVENUE, DashboardCardKeys.VIEW_CE_FUEL_CHARGES,
                    DashboardCardKeys.VIEW_CE_GST_AMOUNT, DashboardCardKeys.VIEW_CE_TOTAL_REVENUE)) {
                payload.setClientEntryKpi(null);
            }
        }

        // Section 5 - Small Client Entry
        if (payload.getSmallClientEntryKpi() != null) {
            CourierDashboardDto.ModuleRangeKpi k = payload.getSmallClientEntryKpi();
            if (!allowed.contains(DashboardCardKeys.VIEW_SCE_TOTAL_BOOKINGS)) {
                k.setTotalBookings(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_SCE_CASH)) {
                k.setCashAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_SCE_GPAY)) {
                k.setGpayAmount(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_SCE_TOTAL_REVENUE)) {
                k.setRevenueTotal(null);
            }
            if (isModuleKpiFullyHidden(allowed, DashboardCardKeys.VIEW_SCE_TOTAL_BOOKINGS,
                    DashboardCardKeys.VIEW_SCE_CASH, DashboardCardKeys.VIEW_SCE_GPAY,
                    DashboardCardKeys.VIEW_SCE_TOTAL_REVENUE)) {
                payload.setSmallClientEntryKpi(null);
            }
        }

        // Section 6 - Customer / Client Summary (AWB groups)
        if (!allowed.contains(DashboardCardKeys.VIEW_CLIENT_ENTRY_SUMMARY)) {
            payload.setClientEntryAwbGroups(new ArrayList<>());
        }
        if (!allowed.contains(DashboardCardKeys.VIEW_SMALL_CLIENT_ENTRY_SUMMARY)) {
            payload.setSmallClientEntryAwbGroups(new ArrayList<>());
        }
        if (!allowed.contains(DashboardCardKeys.VIEW_COLLECTION_CENTER_SUMMARY)) {
            payload.setCollectionCenterAwbGroups(new ArrayList<>());
        }
        if (!allowed.contains(DashboardCardKeys.VIEW_CASH_BOOKING_SUMMARY)) {
            payload.setCashBookingAwbGroups(new ArrayList<>());
        }

        // Section 7 - Ledger Snapshot (accounting)
        if (payload.getAccounting() != null) {
            AccountingSummaryDto a = payload.getAccounting();
            if (!allowed.contains(DashboardCardKeys.VIEW_LEDGER_IN)) {
                a.setTotalIn(null);
                a.setMonthIn(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_LEDGER_OUT)) {
                a.setTotalOut(null);
                a.setMonthOut(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_LEDGER_NET)) {
                a.setCurrentBalance(null);
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_LEDGER_IN)
                    && !allowed.contains(DashboardCardKeys.VIEW_LEDGER_OUT)
                    && !allowed.contains(DashboardCardKeys.VIEW_LEDGER_NET)) {
                payload.setAccounting(null);
            }
        }

        // Section 8 - Charts
        if (!allowed.contains(DashboardCardKeys.VIEW_CHART_DAILY_BOOKING_TREND)) {
            payload.setDailyBookingTrend(new ArrayList<>());
        }
        if (!allowed.contains(DashboardCardKeys.VIEW_CHART_STATUS_DISTRIBUTION)) {
            payload.setStatusDistribution(new ArrayList<>());
        }
        if (!allowed.contains(DashboardCardKeys.VIEW_CHART_CASH_FLOW_TREND)) {
            payload.setCashFlowTrend(new ArrayList<>());
        }
        CourierDashboardDto.CollectionDashboard coll = payload.getCollection();
        if (coll != null) {
            if (!allowed.contains(DashboardCardKeys.VIEW_CHART_DAILY_COLLECTION_TREND)) {
                coll.setDailyCollectionTrend(new ArrayList<>());
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CHART_COLLECTION_AMOUNT_STATUS)) {
                coll.setAmountStatusDistribution(new ArrayList<>());
            }
            if (!allowed.contains(DashboardCardKeys.VIEW_CHART_COLLECTION_TREND_BY_MONTH)) {
                coll.setMonthlyCollectionTrend(new ArrayList<>());
            }
        }
    }

    private static boolean isModuleKpiFullyHidden(Set<String> allowed, String... keys) {
        for (String key : keys) {
            if (allowed.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private CourierDashboardDto.FullPayload buildEmptyDashboardPayload() {
        CourierDashboardDto.Summary summary = CourierDashboardDto.Summary.builder()
                .bookingsToday(0L)
                .clientBookingsCount(0L)
                .shipmentsAllModulesCount(0L)
                .delivered(0L)
                .inTransit(0L)
                .pending(0L)
                .cancelled(0L)
                .failedDeliveries(0L)
                .totalRevenue(BigDecimal.ZERO)
                .monthlyRevenue(BigDecimal.ZERO)
                .codPending(BigDecimal.ZERO)
                .cashBookingsCount(0L)
                .cashBookingRevenue(BigDecimal.ZERO)
                .deliveredCashBookings(0L)
                .revenueRangeActive(false)
                .build();
        return CourierDashboardDto.FullPayload.builder()
                .summary(summary)
                .dailyBookingTrend(List.of())
                .monthlyRevenueTrend(List.of())
                .statusDistribution(List.of())
                .topClientsByRevenue(List.of())
                .latestDelivered(List.of())
                .failedDeliveries(List.of())
                .clientEntryAwbGroups(List.of())
                .collectionCenterAwbGroups(List.of())
                .cashBookingAwbGroups(List.of())
                .smallClientEntryAwbGroups(List.of())
                .build();
    }
}

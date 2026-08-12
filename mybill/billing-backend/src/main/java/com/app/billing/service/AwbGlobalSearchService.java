package com.app.billing.service;

import com.app.billing.dao.CashBookingRepository;
import com.app.billing.dao.CollectionCenterEntryRepository;
import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.dto.AwbGlobalSearchHitDto;
import com.app.billing.model.CashBooking;
import com.app.billing.model.CollectionCenterEntry;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.MonthlyCourierQuotation;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.model.SmallClientEntryQuotation;
import com.app.billing.util.CourierTrackingNumberValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AwbGlobalSearchService {

    private final MonthlyCourierEntryRepository monthlyCourierEntryRepository;
    private final SmallClientEntryRepository smallClientEntryRepository;
    private final CollectionCenterEntryRepository collectionCenterEntryRepository;
    private final CashBookingRepository cashBookingRepository;
    private final MonthlyCourierQuotationService monthlyCourierQuotationService;
    private final SmallClientEntryQuotationService smallClientEntryQuotationService;

    /**
     * Search AWBs across Client Entry, Small Client Entry, Collection Center, and Cash Booking.
     * All provided filters (AWB, date range, courier type) are combined with AND logic.
     */
    public List<AwbGlobalSearchHitDto> search(String rawQuery, LocalDate fromDate, LocalDate toDate,
            String rawCourierType) {
        String awbNorm = CourierTrackingNumberValidator.normalizeOrNull(rawQuery);
        String courierNorm = normalizeCourier(rawCourierType);
        boolean hasAwb = awbNorm != null;
        boolean hasDateFilter = fromDate != null || toDate != null;
        boolean hasCourier = courierNorm != null;

        if (!hasAwb && !hasDateFilter && !hasCourier) {
            return List.of();
        }

        List<AwbGlobalSearchHitDto> out = new ArrayList<>();
        Map<String, MonthlyCourierQuotation> clientQuotations = new HashMap<>();
        Map<String, SmallClientEntryQuotation> smallClientQuotations = new HashMap<>();

        for (MonthlyCourierEntry e : fetchMonthlyEntries(awbNorm, fromDate, toDate, courierNorm)) {
            out.add(toClientEntryHit(e, clientQuotations));
        }
        for (SmallClientEntry e : fetchSmallClientEntries(awbNorm, fromDate, toDate, courierNorm)) {
            out.add(toSmallClientEntryHit(e, smallClientQuotations));
        }
        for (CollectionCenterEntry e : fetchCollectionCenterEntries(awbNorm, fromDate, toDate, courierNorm)) {
            out.add(toCollectionCenterHit(e));
        }
        for (CashBooking e : fetchCashBookings(awbNorm, fromDate, toDate, courierNorm)) {
            out.add(toCashBookingHit(e));
        }

        out.sort(Comparator
                .comparing(AwbGlobalSearchHitDto::getEntryDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AwbGlobalSearchHitDto::getAwbNo, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    /** Legacy AWB-only lookup. */
    public List<AwbGlobalSearchHitDto> searchAll(String rawQuery) {
        return search(rawQuery, null, null, null);
    }

    private List<MonthlyCourierEntry> fetchMonthlyEntries(String awbNorm, LocalDate fromDate, LocalDate toDate,
            String courierNorm) {
        List<MonthlyCourierEntry> candidates = resolveByDate(
                fromDate,
                toDate,
                monthlyCourierEntryRepository::findByEntryDate,
                monthlyCourierEntryRepository::findByEntryDateBetween,
                monthlyCourierEntryRepository::findByEntryDateGreaterThanEqual,
                monthlyCourierEntryRepository::findByEntryDateLessThanEqual);

        if (candidates.isEmpty() && fromDate == null && toDate == null) {
            if (awbNorm != null) {
                monthlyCourierEntryRepository.findFirstByTrackingNumberIgnoreCase(awbNorm)
                        .ifPresent(candidates::add);
            } else if (courierNorm != null) {
                candidates.addAll(monthlyCourierEntryRepository.findByCourierTypeIgnoreCase(courierNorm));
            }
        }

        return candidates.stream()
                .filter(e -> hasNonBlankAwb(e.getTrackingNumber()))
                .filter(e -> matchesAwbFilter(awbNorm, e.getTrackingNumber()))
                .filter(e -> matchesCourierFilter(courierNorm, e.getCourierType()))
                .toList();
    }

    private List<SmallClientEntry> fetchSmallClientEntries(String awbNorm, LocalDate fromDate, LocalDate toDate,
            String courierNorm) {
        List<SmallClientEntry> candidates = resolveByDate(
                fromDate,
                toDate,
                smallClientEntryRepository::findByEntryDate,
                smallClientEntryRepository::findByEntryDateBetween,
                smallClientEntryRepository::findByEntryDateGreaterThanEqual,
                smallClientEntryRepository::findByEntryDateLessThanEqual);

        if (candidates.isEmpty() && fromDate == null && toDate == null) {
            if (awbNorm != null) {
                smallClientEntryRepository.findFirstByTrackingNumberIgnoreCase(awbNorm)
                        .ifPresent(candidates::add);
            } else if (courierNorm != null) {
                candidates.addAll(smallClientEntryRepository.findByCourierTypeIgnoreCase(courierNorm));
            }
        }

        return candidates.stream()
                .filter(e -> hasNonBlankAwb(e.getTrackingNumber()))
                .filter(e -> matchesAwbFilter(awbNorm, e.getTrackingNumber()))
                .filter(e -> matchesCourierFilter(courierNorm, e.getCourierType()))
                .toList();
    }

    private List<CollectionCenterEntry> fetchCollectionCenterEntries(String awbNorm, LocalDate fromDate,
            LocalDate toDate, String courierNorm) {
        List<CollectionCenterEntry> candidates = resolveByDate(
                fromDate,
                toDate,
                collectionCenterEntryRepository::findByEntryDate,
                collectionCenterEntryRepository::findByEntryDateBetween,
                collectionCenterEntryRepository::findByEntryDateGreaterThanEqual,
                collectionCenterEntryRepository::findByEntryDateLessThanEqual);

        if (candidates.isEmpty() && fromDate == null && toDate == null) {
            if (awbNorm != null) {
                collectionCenterEntryRepository.findFirstByAwbNoIgnoreCase(awbNorm)
                        .ifPresent(candidates::add);
            } else if (courierNorm != null) {
                candidates.addAll(collectionCenterEntryRepository.findByCourierIgnoreCase(courierNorm));
            }
        }

        return candidates.stream()
                .filter(e -> hasNonBlankAwb(e.getAwbNo()))
                .filter(e -> matchesAwbFilter(awbNorm, e.getAwbNo()))
                .filter(e -> matchesCourierFilter(courierNorm, e.getCourier()))
                .toList();
    }

    private List<CashBooking> fetchCashBookings(String awbNorm, LocalDate fromDate, LocalDate toDate,
            String courierNorm) {
        List<CashBooking> candidates = resolveByBookingDate(fromDate, toDate);

        if (candidates.isEmpty() && fromDate == null && toDate == null) {
            if (awbNorm != null) {
                cashBookingRepository.findFirstByAwbNoIgnoreCase(awbNorm).ifPresent(candidates::add);
            } else if (courierNorm != null) {
                candidates.addAll(cashBookingRepository.findByCourierIgnoreCase(courierNorm));
            }
        }

        return candidates.stream()
                .filter(e -> hasNonBlankAwb(e.getAwbNo()))
                .filter(e -> matchesAwbFilter(awbNorm, e.getAwbNo()))
                .filter(e -> matchesCourierFilter(courierNorm, e.getCourier()))
                .toList();
    }

    /**
     * Resolves entries for a date filter. When from and to are the same day, uses an exact-day
     * query so single-day searches return results reliably.
     */
    private <T> List<T> resolveByDate(
            LocalDate fromDate,
            LocalDate toDate,
            Function<LocalDate, List<T>> onExactDay,
            DateBetweenQuery<T> between,
            DateFromQuery<T> fromOnly,
            DateToQuery<T> toOnly) {
        if (fromDate == null && toDate == null) {
            return new ArrayList<>();
        }
        if (fromDate != null && toDate != null) {
            if (fromDate.equals(toDate)) {
                return new ArrayList<>(onExactDay.apply(fromDate));
            }
            if (fromDate.isAfter(toDate)) {
                return List.of();
            }
            return new ArrayList<>(between.find(fromDate, toDate));
        }
        if (fromDate != null) {
            return new ArrayList<>(fromOnly.find(fromDate));
        }
        return new ArrayList<>(toOnly.find(toDate));
    }

    private List<CashBooking> resolveByBookingDate(LocalDate fromDate, LocalDate toDate) {
        return resolveByDate(
                fromDate,
                toDate,
                cashBookingRepository::findByBookingDate,
                cashBookingRepository::findByBookingDateBetween,
                cashBookingRepository::findByBookingDateGreaterThanEqual,
                cashBookingRepository::findByBookingDateLessThanEqual);
    }

    private AwbGlobalSearchHitDto toClientEntryHit(MonthlyCourierEntry e,
            Map<String, MonthlyCourierQuotation> cache) {
        MonthlyCourierQuotation q = cache.computeIfAbsent(e.getMonthlyQuotationId(),
                id -> monthlyCourierQuotationService.findEntityById(id));
        String awb = e.getTrackingNumber().trim();
        String recv = e.getReceiverName() != null ? e.getReceiverName()
                : (e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "");
        Map<String, String> qp = new LinkedHashMap<>();
        qp.put("awb", awb);
        qp.put("entryId", e.getId());
        return AwbGlobalSearchHitDto.builder()
                .moduleCode("CLIENT_ENTRY")
                .moduleLabel("Client Entry")
                .awbNo(awb)
                .receiverName(recv)
                .customerOrConsignor(q.getCustomerName() != null ? q.getCustomerName() : "")
                .entryDate(e.getEntryDate())
                .courierType(e.getCourierType())
                .status(e.getDeliveryStatus())
                .amount(e.getAmount())
                .path("/client-entries/edit/" + e.getMonthlyQuotationId())
                .queryParams(qp)
                .build();
    }

    private AwbGlobalSearchHitDto toSmallClientEntryHit(SmallClientEntry e,
            Map<String, SmallClientEntryQuotation> cache) {
        SmallClientEntryQuotation q = cache.computeIfAbsent(e.getMonthlyQuotationId(),
                id -> smallClientEntryQuotationService.findEntityById(id));
        String awb = e.getTrackingNumber().trim();
        String recv = e.getReceiverName() != null ? e.getReceiverName()
                : (e.getConsigneeAddress() != null ? e.getConsigneeAddress() : "");
        Map<String, String> qp = new LinkedHashMap<>();
        qp.put("awb", awb);
        qp.put("entryId", e.getId());
        return AwbGlobalSearchHitDto.builder()
                .moduleCode("SMALL_CLIENT_ENTRY")
                .moduleLabel("Small Client Entry")
                .awbNo(awb)
                .receiverName(recv)
                .customerOrConsignor(q != null && q.getCustomerName() != null ? q.getCustomerName()
                        : (e.getConsignor() != null ? e.getConsignor() : ""))
                .entryDate(e.getEntryDate())
                .courierType(e.getCourierType())
                .status(e.getDeliveryStatus())
                .amount(e.getAmount())
                .path("/small-client-entries/edit/" + e.getMonthlyQuotationId())
                .queryParams(qp)
                .build();
    }

    private AwbGlobalSearchHitDto toCollectionCenterHit(CollectionCenterEntry e) {
        String awb = e.getAwbNo().trim();
        return AwbGlobalSearchHitDto.builder()
                .moduleCode("COLLECTION_CENTER")
                .moduleLabel("Collection Center")
                .awbNo(awb)
                .receiverName(e.getReceiverName())
                .customerOrConsignor(e.getCustomerName() != null ? e.getCustomerName() : e.getConsignor())
                .entryDate(e.getEntryDate())
                .courierType(e.getCourier())
                .status(e.getStatus())
                .amount(e.getAmount())
                .path("/client-entries/collection-center/edit/" + e.getId())
                .queryParams(Map.of())
                .build();
    }

    private AwbGlobalSearchHitDto toCashBookingHit(CashBooking e) {
        String awb = e.getAwbNo().trim();
        return AwbGlobalSearchHitDto.builder()
                .moduleCode("CASH_BOOKING")
                .moduleLabel("Cash Booking")
                .awbNo(awb)
                .receiverName(e.getReceiverName())
                .customerOrConsignor("")
                .entryDate(e.getBookingDate())
                .courierType(e.getCourier())
                .status(e.getStatus())
                .amount(e.getAmount())
                .path("/cash-booking/edit/" + e.getId())
                .queryParams(Map.of())
                .build();
    }

    private static String normalizeCourier(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean hasNonBlankAwb(String awb) {
        return awb != null && !awb.isBlank();
    }

    private static boolean matchesAwbFilter(String normalizedFilter, String awbRaw) {
        if (normalizedFilter == null) {
            return true;
        }
        if (awbRaw == null) {
            return false;
        }
        return normalizedFilter.equalsIgnoreCase(awbRaw.trim());
    }

    private static boolean matchesCourierFilter(String normalizedFilter, String courierRaw) {
        if (normalizedFilter == null) {
            return true;
        }
        if (courierRaw == null || courierRaw.isBlank()) {
            return false;
        }
        return normalizedFilter.equalsIgnoreCase(courierRaw.trim());
    }

    @FunctionalInterface
    private interface DateBetweenQuery<T> {
        List<T> find(LocalDate start, LocalDate end);
    }

    @FunctionalInterface
    private interface DateFromQuery<T> {
        List<T> find(LocalDate start);
    }

    @FunctionalInterface
    private interface DateToQuery<T> {
        List<T> find(LocalDate end);
    }
}

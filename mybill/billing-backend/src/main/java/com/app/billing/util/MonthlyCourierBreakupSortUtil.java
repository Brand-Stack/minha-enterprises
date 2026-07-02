package com.app.billing.util;

import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.dto.SmallClientEntryDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts monthly shipment breakup rows for export by {@link MonthlyCourierEntryDto#getEntryDate()}
 * ascending; null dates last (stable tie-breaker by id).
 */
public final class MonthlyCourierBreakupSortUtil {

    private MonthlyCourierBreakupSortUtil() {
    }

    public static List<MonthlyCourierEntryDto> sortedCopy(List<MonthlyCourierEntryDto> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : new ArrayList<>(entries);
        }
        List<MonthlyCourierEntryDto> copy = new ArrayList<>(entries);
        copy.sort(Comparator
                .comparing(MonthlyCourierBreakupSortUtil::entryDateKey, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e.getId() != null ? e.getId() : "", String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    public static List<SmallClientEntryDto> sortedCopySmallClient(List<SmallClientEntryDto> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : new ArrayList<>(entries);
        }
        List<SmallClientEntryDto> copy = new ArrayList<>(entries);
        copy.sort(Comparator
                .comparing(SmallClientEntryDto::getEntryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e.getId() != null ? e.getId() : "", String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    private static LocalDate entryDateKey(MonthlyCourierEntryDto e) {
        return e.getEntryDate();
    }
}

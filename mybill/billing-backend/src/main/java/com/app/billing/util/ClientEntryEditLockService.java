package com.app.billing.util;

import com.app.billing.service.PermissionEvaluatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Month-end locking for Client Entry and Monthly Shipment Breakup edits.
 * Previous-month records are editable only until the 5th day of the current month (non-admin).
 */
@Service
@RequiredArgsConstructor
public class ClientEntryEditLockService {

    public static final String LOCK_MESSAGE =
            "Editing is allowed only until the 5th day of the following month. Please contact an administrator.";

    private static final String[] MONTH_NAMES = {
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
    };

    private final PermissionEvaluatorService permissionEvaluatorService;

    public void enforceEditAllowed(String month, Integer year) {
        if (isRecordLocked(month, year) && !permissionEvaluatorService.isCurrentUserAdmin()) {
            throw new AccessDeniedException(LOCK_MESSAGE);
        }
    }

    public boolean isRecordLocked(String month, Integer year) {
        return isRecordLocked(month, year, LocalDate.now());
    }

    boolean isRecordLocked(String month, Integer year, LocalDate today) {
        YearMonth recordPeriod = toYearMonth(month, year);
        if (recordPeriod == null) {
            return false;
        }
        YearMonth currentPeriod = YearMonth.from(today);
        if (!recordPeriod.isBefore(currentPeriod)) {
            return false;
        }
        return today.getDayOfMonth() > 5;
    }

    public boolean canCurrentUserEdit(String month, Integer year) {
        return !isRecordLocked(month, year) || permissionEvaluatorService.isCurrentUserAdmin();
    }

    private YearMonth toYearMonth(String month, Integer year) {
        if (year == null || month == null || month.isBlank()) {
            return null;
        }
        int monthIndex = monthIndex(month);
        if (monthIndex < 1 || monthIndex > 12) {
            return null;
        }
        return YearMonth.of(year, monthIndex);
    }

    private static int monthIndex(String month) {
        String u = month.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (MONTH_NAMES[i].equals(u)) {
                return i + 1;
            }
        }
        return 0;
    }
}

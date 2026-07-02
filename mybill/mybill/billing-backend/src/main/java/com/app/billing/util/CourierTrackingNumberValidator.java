package com.app.billing.util;

import java.util.regex.Pattern;

/**
 * AWB / tracking number rules: optional blank; otherwise letters and/or digits only.
 * Duplicate checks (numeric-only) are done in the service via repository.
 */
public final class CourierTrackingNumberValidator {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9]+$");

    private CourierTrackingNumberValidator() {
    }

    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    /** True if non-null and every character is a digit. */
    public static boolean isPurelyNumeric(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trackingNumber.length(); i++) {
            if (!Character.isDigit(trackingNumber.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @throws IllegalArgumentException if non-blank and contains disallowed characters
     */
    public static void validateFormat(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isEmpty()) {
            return;
        }
        if (!ALLOWED.matcher(trackingNumber).matches()) {
            throw new IllegalArgumentException(
                    "Tracking number (AWB) may contain only letters and digits.");
        }
    }
}

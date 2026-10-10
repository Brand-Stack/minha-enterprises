package com.app.billing.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Maps free-text / dropdown amount status values into dashboard buckets.
 * Cash→Cash, GPay→Online, Pending→Pending, COD→CashOnDelivery; Paid/UnPaid handled as distinct where needed.
 */
public final class AmountStatusBucketUtil {

    public enum Bucket {
        CASH,
        GPAY,
        PENDING,
        COD,
        PAID,
        OTHER
    }

    private AmountStatusBucketUtil() {
    }

    public static Bucket classify(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Bucket.OTHER;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if ("COD".equals(u) || u.contains("CASHONDELIVERY") || u.contains("CASH ON DELIVERY")) {
            return Bucket.COD;
        }
        if (u.contains("GPAY") || "ONLINE".equals(u)) {
            return Bucket.GPAY;
        }
        if (u.contains("PENDING") || "UNPAID".equals(u)) {
            return Bucket.PENDING;
        }
        if ("CASH".equals(u)) {
            return Bucket.CASH;
        }
        if ("PAID".equals(u)) {
            return Bucket.PAID;
        }
        return Bucket.OTHER;
    }
}

package com.app.billing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierTrackingNumberValidatorTest {

    @Test
    void normalize_trimsAndEmptyToNull() {
        assertNull(CourierTrackingNumberValidator.normalizeOrNull("   "));
        assertEquals("ABC123", CourierTrackingNumberValidator.normalizeOrNull("  ABC123  "));
    }

    @Test
    void isPurelyNumeric() {
        assertTrue(CourierTrackingNumberValidator.isPurelyNumeric("12345"));
        assertFalse(CourierTrackingNumberValidator.isPurelyNumeric("12A45"));
        assertFalse(CourierTrackingNumberValidator.isPurelyNumeric(""));
    }

    @Test
    void validateFormat_rejectsSpecialChars() {
        assertThrows(IllegalArgumentException.class,
                () -> CourierTrackingNumberValidator.validateFormat("12-34"));
    }

    @Test
    void validateFormat_allowsAlphanumeric() {
        CourierTrackingNumberValidator.validateFormat("AWB123abc");
    }
}

package com.app.billing.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientEntryEditLockServiceTest {

  private final ClientEntryEditLockService service = new ClientEntryEditLockService(null);

  @Test
  void currentMonth_isNotLocked() {
    assertFalse(service.isRecordLocked("JUNE", 2026, LocalDate.of(2026, 6, 15)));
  }

  @Test
  void futureMonth_isNotLocked() {
    assertFalse(service.isRecordLocked("JULY", 2026, LocalDate.of(2026, 6, 15)));
  }

  @Test
  void previousMonth_lockStatus() {
    // <= 5th: previous month is NOT locked
    assertFalse(service.isRecordLocked("MAY", 2026, LocalDate.of(2026, 6, 3)));
    // > 5th: previous month IS locked
    assertTrue(service.isRecordLocked("MAY", 2026, LocalDate.of(2026, 6, 15)));
    assertTrue(service.isRecordLocked("MAY", 2026, LocalDate.of(2026, 6, 28)));
  }

  @Test
  void olderMonth_isLocked() {
    // Older month is always locked regardless of day of month
    assertTrue(service.isRecordLocked("APRIL", 2026, LocalDate.of(2026, 6, 3)));
    assertTrue(service.isRecordLocked("APRIL", 2026, LocalDate.of(2026, 6, 10)));
    assertTrue(service.isRecordLocked("APRIL", 2026, LocalDate.of(2026, 6, 30)));
  }
}

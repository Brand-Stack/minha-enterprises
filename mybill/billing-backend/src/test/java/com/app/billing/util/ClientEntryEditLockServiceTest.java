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
  void previousMonth_beforeFifth_isNotLocked() {
    assertFalse(service.isRecordLocked("MAY", 2026, LocalDate.of(2026, 6, 3)));
  }

  @Test
  void previousMonth_onFifth_isNotLocked() {
    assertFalse(service.isRecordLocked("MAY", 2026, LocalDate.of(2026, 6, 5)));
  }

  @Test
  void previousMonth_afterFifth_isLocked() {
    assertTrue(service.isRecordLocked("MAY", 2026, LocalDate.of(2026, 6, 6)));
  }

  @Test
  void olderMonth_afterFifth_isLocked() {
    assertTrue(service.isRecordLocked("APRIL", 2026, LocalDate.of(2026, 6, 10)));
  }
}

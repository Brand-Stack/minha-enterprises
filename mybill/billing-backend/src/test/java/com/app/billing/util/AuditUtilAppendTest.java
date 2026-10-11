package com.app.billing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditUtilAppendTest {

  private final AuditUtil auditUtil = new AuditUtil();

  @Test
  void appendLastUpdatedBy_startsFreshWhenEmpty() {
    assertEquals("John", auditUtil.appendLastUpdatedBy(null, "John"));
  }

  @Test
  void appendLastUpdatedBy_appendsNewName() {
    assertEquals("John, Ravi", auditUtil.appendLastUpdatedBy("John", "Ravi"));
  }

  @Test
  void appendLastUpdatedBy_skipsConsecutiveDuplicate() {
    assertEquals("John, Ravi", auditUtil.appendLastUpdatedBy("John, Ravi", "Ravi"));
  }

  @Test
  void appendLastUpdatedBy_appendsAfterDifferentName() {
    assertEquals("John, Ravi, Admin", auditUtil.appendLastUpdatedBy("John, Ravi", "Admin"));
  }
}

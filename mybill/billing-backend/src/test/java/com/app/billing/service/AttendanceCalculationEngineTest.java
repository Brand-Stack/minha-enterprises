package com.app.billing.service;

import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.AttendancePunch;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.PermissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;
import com.app.billing.dao.HolidayRepository;

class AttendanceCalculationEngineTest {

    private AttendanceCalculationEngine engine;
    private CompanySettingsDto settings;
    private HolidayRepository holidayRepository;

    @BeforeEach
    void setUp() {
        holidayRepository = Mockito.mock(HolidayRepository.class);
        engine = new AttendanceCalculationEngine(holidayRepository);
        settings = CompanySettingsDto.builder()
                .workingStartTime("10:00")
                .workingEndTime("18:00")
                .workingHoursPerDay(8.0)
                .lateGracePeriodMinutes(15)
                .earlyCheckoutGracePeriodMinutes(15)
                .breakDurationMinutes(45)
                .overtimeThresholdMinutes(30)
                .workingDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"))
                .weekendDays(List.of("SUNDAY"))
                .build();
    }

    @Test
    void testNormalAttendanceOnTime() {
        LocalDate date = LocalDate.of(2026, 9, 14); // Monday
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(9, 58))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(18, 05))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertEquals(AttendanceRecord.AttendanceStatus.PRESENT, record.getStatus());
        assertFalse(record.getLateArrival());
        assertFalse(record.getEarlyDeparture());
        assertTrue(record.getTotalWorkingHours() >= 7.0);
    }

    @Test
    void testLateArrivalWithinGracePeriod() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 10))).build()); // 10 mins late, within 15 min grace
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(18, 00))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertFalse(record.getLateArrival());
        assertEquals(AttendanceRecord.AttendanceStatus.PRESENT, record.getStatus());
    }

    @Test
    void testLateArrivalBeyondGracePeriod() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 30))).build()); // 30 mins late
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(18, 00))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertTrue(record.getLateArrival());
        assertEquals(30, record.getLateMinutes());
        assertEquals(AttendanceRecord.AttendanceStatus.LATE, record.getStatus());
    }

    @Test
    void testLateArrivalCoveredByApprovedPermission() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 45))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(18, 00))).build());

        PermissionRequest permission = PermissionRequest.builder()
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(PermissionRequest.PermissionStatus.APPROVED)
                .build();

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, permission);

        assertFalse(record.getLateArrival());
        assertEquals(AttendanceRecord.AttendanceStatus.PERMISSION, record.getStatus());
    }

    @Test
    void testOvertimeCalculation() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 0))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(20, 00))).build()); // 2 hours overtime

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertEquals(2.0, record.getOvertimeHours());
        assertEquals(AttendanceRecord.AttendanceStatus.OVERTIME, record.getStatus());
    }

    @Test
    void testNonDefaultShiftTimingsAndConfiguredWorkingHours() {
        // Shift 08:30 to 16:30 (8h span), expected working hours = 7.0 hours
        CompanySettingsDto customShift = CompanySettingsDto.builder()
                .workingStartTime("08:30")
                .workingEndTime("16:30")
                .workingHoursPerDay(7.0)
                .lateGracePeriodMinutes(10)
                .earlyCheckoutGracePeriodMinutes(10)
                .breakDurationMinutes(30)
                .overtimeThresholdMinutes(30)
                .workingDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
                .weekendDays(List.of("SATURDAY", "SUNDAY"))
                .build();

        LocalDate date = LocalDate.of(2026, 9, 15); // Tuesday
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(8, 30))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(16, 30))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, customShift, null, null);

        assertEquals(7.0, record.getExpectedWorkingHours());
        assertEquals(7.5, record.getTotalWorkingHours()); // 8h - 30m break = 7.5h
        // Overtime threshold is 30 mins: 7.5h vs 7.0h expected => 0.5h excess >= 30m
        assertEquals(0.5, record.getOvertimeHours());
        assertEquals(AttendanceRecord.AttendanceStatus.OVERTIME, record.getStatus());
    }

    @Test
    void testEarlyCheckoutBeyondGracePeriod() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        // In on time, leaves at 17:15 (expected 18:00, grace is 15 min, so min checkout is 17:45)
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 0))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(17, 15))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertTrue(record.getEarlyDeparture());
        assertEquals(45, record.getEarlyMinutes());
        assertEquals(AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT, record.getStatus());
    }

    @Test
    void testEarlyCheckoutWithinGracePeriod() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        // In on time, leaves at 17:50 (within 15 min grace of 18:00)
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 0))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(17, 50))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertFalse(record.getEarlyDeparture());
        assertEquals(AttendanceRecord.AttendanceStatus.PRESENT, record.getStatus());
    }

    @Test
    void testCombinedLateArrivalAndEarlyCheckout() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        // Arrives 10:30 (late), leaves 17:00 (early)
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 30))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(date, LocalTime.of(17, 00))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertTrue(record.getLateArrival());
        assertTrue(record.getEarlyDeparture());
        assertEquals(AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT, record.getStatus());
    }

    @Test
    void testOvernightShiftCalculation() {
        // Overnight Shift: 22:00 to 06:00 next day
        CompanySettingsDto overnightSettings = CompanySettingsDto.builder()
                .workingStartTime("22:00")
                .workingEndTime("06:00")
                .workingHoursPerDay(7.5)
                .lateGracePeriodMinutes(15)
                .earlyCheckoutGracePeriodMinutes(15)
                .breakDurationMinutes(30)
                .overtimeThresholdMinutes(30)
                .workingDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
                .weekendDays(List.of("SATURDAY", "SUNDAY"))
                .build();

        LocalDate shiftDate = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        // Punches: IN at 22:05 on 14th, OUT at 06:10 on 15th
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(shiftDate, LocalTime.of(22, 5))).build());
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.OUT).timestamp(LocalDateTime.of(shiftDate.plusDays(1), LocalTime.of(6, 10))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", shiftDate, punches, overnightSettings, null, null);

        assertFalse(record.getLateArrival());
        assertFalse(record.getEarlyDeparture());
        assertEquals(7.58, record.getTotalWorkingHours(), 0.1);
        assertEquals(AttendanceRecord.AttendanceStatus.PRESENT, record.getStatus());
    }

    @Test
    void testIncompleteAttendanceSinglePunch() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        List<AttendancePunch> punches = new ArrayList<>();
        punches.add(AttendancePunch.builder().type(AttendancePunch.PunchType.IN).timestamp(LocalDateTime.of(date, LocalTime.of(10, 0))).build());

        AttendanceRecord record = engine.calculateDailyAttendance("EMP1", "EMP001", "John Doe", "IT", date, punches, settings, null, null);

        assertEquals(AttendanceRecord.AttendanceStatus.INCOMPLETE, record.getStatus());
    }
}

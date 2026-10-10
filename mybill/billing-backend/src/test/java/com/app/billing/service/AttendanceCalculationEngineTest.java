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
}

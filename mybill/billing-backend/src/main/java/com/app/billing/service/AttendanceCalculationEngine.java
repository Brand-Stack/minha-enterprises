package com.app.billing.service;

import com.app.billing.dao.HolidayRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.AttendancePunch;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.LeaveRequest;
import com.app.billing.model.PermissionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCalculationEngine {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final HolidayRepository holidayRepository;

    /**
     * Calculate daily attendance record based on configured company policy, punches,
     * holiday calendar, leave status, and permission requests.
     */
    public AttendanceRecord calculateDailyAttendance(
            String employeeId,
            String employeeCode,
            String employeeName,
            String department,
            LocalDate date,
            List<AttendancePunch> punches,
            CompanySettingsDto settings,
            LeaveRequest approvedLeave,
            PermissionRequest approvedPermission) {

        boolean isHoliday = holidayRepository != null
                ? holidayRepository.findByHolidayDate(date).map(h -> Boolean.TRUE.equals(h.getActive())).orElse(false)
                : false;

        return calculateDailyAttendance(employeeId, employeeCode, employeeName, department, date, punches, settings, approvedLeave, approvedPermission, isHoliday);
    }

    public AttendanceRecord calculateDailyAttendance(
            String employeeId,
            String employeeCode,
            String employeeName,
            String department,
            LocalDate date,
            List<AttendancePunch> punches,
            CompanySettingsDto settings,
            LeaveRequest approvedLeave,
            PermissionRequest approvedPermission,
            boolean isHoliday) {

        double expectedHours = settings.getWorkingHoursPerDay() != null ? settings.getWorkingHoursPerDay() : 8.0;
        int breakMinutes = settings.getBreakDurationMinutes() != null ? settings.getBreakDurationMinutes() : 45;
        int lateGraceMinutes = settings.getLateGracePeriodMinutes() != null ? settings.getLateGracePeriodMinutes() : 15;
        int earlyGraceMinutes = settings.getEarlyCheckoutGracePeriodMinutes() != null ? settings.getEarlyCheckoutGracePeriodMinutes() : 15;
        int overtimeThreshold = settings.getOvertimeThresholdMinutes() != null ? settings.getOvertimeThresholdMinutes() : 30;

        LocalTime expectedStart = parseTime(settings.getWorkingStartTime(), LocalTime.of(10, 0));
        LocalTime expectedEnd = parseTime(settings.getWorkingEndTime(), LocalTime.of(18, 0));

        AttendanceRecord.AttendanceStatus status = AttendanceRecord.AttendanceStatus.PRESENT;
        AttendancePunch.PunchSource mainSource = AttendancePunch.PunchSource.MANUAL;

        // Precedence: Holiday (H) > Week Off (WO) > Approved Leave (LEAVE) > Present (P) / Absent (A)
        String dayOfWeek = date.getDayOfWeek().name();
        boolean isWeekend = settings.getWeekendDays() != null && settings.getWeekendDays().contains(dayOfWeek);

        if (isHoliday) {
            status = AttendanceRecord.AttendanceStatus.HOLIDAY;
        } else if (isWeekend) {
            status = AttendanceRecord.AttendanceStatus.WEEK_OFF;
        } else if (approvedLeave != null) {
            if (Boolean.TRUE.equals(approvedLeave.getIsHalfDay())) {
                status = AttendanceRecord.AttendanceStatus.HALF_DAY;
            } else {
                status = AttendanceRecord.AttendanceStatus.LEAVE;
            }
        }

        if (punches == null || punches.isEmpty()) {
            if (status != AttendanceRecord.AttendanceStatus.LEAVE
                    && status != AttendanceRecord.AttendanceStatus.HOLIDAY
                    && status != AttendanceRecord.AttendanceStatus.WEEK_OFF) {
                status = AttendanceRecord.AttendanceStatus.ABSENT;
            }
            return AttendanceRecord.builder()
                    .employeeId(employeeId)
                    .employeeCode(employeeCode)
                    .employeeName(employeeName)
                    .department(department)
                    .date(date)
                    .expectedWorkingHours(expectedHours)
                    .totalWorkingHours(0.0)
                    .breakDurationMinutes(breakMinutes)
                    .lateArrival(false)
                    .lateMinutes(0)
                    .earlyDeparture(false)
                    .earlyMinutes(0)
                    .overtimeHours(0.0)
                    .status(status)
                    .sourceOfPunch(mainSource)
                    .punches(List.of())
                    .build();
        }

        // Sort punches chronologically
        punches.sort(Comparator.comparing(AttendancePunch::getTimestamp));
        mainSource = punches.get(0).getSource() != null ? punches.get(0).getSource() : AttendancePunch.PunchSource.MANUAL;

        LocalDateTime firstCheckIn = null;
        LocalDateTime lastCheckOut = null;

        // Find first IN punch
        for (AttendancePunch p : punches) {
            if (p.getType() == AttendancePunch.PunchType.IN) {
                firstCheckIn = p.getTimestamp();
                break;
            }
        }
        if (firstCheckIn == null) {
            firstCheckIn = punches.get(0).getTimestamp();
        }

        // Find last OUT punch
        for (int i = punches.size() - 1; i >= 0; i--) {
            AttendancePunch p = punches.get(i);
            if (p.getType() == AttendancePunch.PunchType.OUT) {
                lastCheckOut = p.getTimestamp();
                break;
            }
        }
        if (lastCheckOut == null && punches.size() > 1) {
            lastCheckOut = punches.get(punches.size() - 1).getTimestamp();
        }

        // Calculate actual working hours (sum of IN-OUT spans minus break)
        double totalWorkedMinutes = 0;
        if (firstCheckIn != null && lastCheckOut != null && lastCheckOut.isAfter(firstCheckIn)) {
            totalWorkedMinutes = Duration.between(firstCheckIn, lastCheckOut).toMinutes();
            if (totalWorkedMinutes > breakMinutes) {
                totalWorkedMinutes -= breakMinutes;
            }
        }
        double totalWorkingHours = Math.round((totalWorkedMinutes / 60.0) * 100.0) / 100.0;

        // Evaluate Late Arrival
        boolean lateArrival = false;
        int lateMinutes = 0;
        if (firstCheckIn != null) {
            LocalTime checkInTime = firstCheckIn.toLocalTime();
            LocalTime maxAllowedStart = expectedStart.plusMinutes(lateGraceMinutes);
            if (checkInTime.isAfter(maxAllowedStart)) {
                lateMinutes = (int) Duration.between(expectedStart, checkInTime).toMinutes();
                if (approvedPermission != null && approvedPermission.getStartTime() != null) {
                    if (!checkInTime.isAfter(approvedPermission.getEndTime())) {
                        lateArrival = false;
                    } else {
                        lateArrival = true;
                    }
                } else {
                    lateArrival = true;
                }
            }
        }

        // Evaluate Early Departure
        boolean earlyDeparture = false;
        int earlyMinutes = 0;
        if (lastCheckOut != null) {
            LocalTime checkOutTime = lastCheckOut.toLocalTime();
            LocalTime minAllowedEnd = expectedEnd.minusMinutes(earlyGraceMinutes);
            if (checkOutTime.isBefore(minAllowedEnd)) {
                earlyMinutes = (int) Duration.between(checkOutTime, expectedEnd).toMinutes();
                if (approvedPermission != null && approvedPermission.getEndTime() != null) {
                    if (!checkOutTime.isBefore(approvedPermission.getStartTime())) {
                        earlyDeparture = false;
                    } else {
                        earlyDeparture = true;
                    }
                } else {
                    earlyDeparture = true;
                }
            }
        }

        // Evaluate Overtime
        double overtimeHours = 0.0;
        if (lastCheckOut != null) {
            LocalTime checkOutTime = lastCheckOut.toLocalTime();
            if (checkOutTime.isAfter(expectedEnd.plusMinutes(overtimeThreshold))) {
                long otMinutes = Duration.between(expectedEnd, checkOutTime).toMinutes();
                overtimeHours = Math.round((otMinutes / 60.0) * 100.0) / 100.0;
            }
        }

        // Determine Status if not already HOLIDAY, WEEK_OFF, or LEAVE
        if (status != AttendanceRecord.AttendanceStatus.LEAVE
                && status != AttendanceRecord.AttendanceStatus.HALF_DAY
                && status != AttendanceRecord.AttendanceStatus.HOLIDAY
                && status != AttendanceRecord.AttendanceStatus.WEEK_OFF) {
            if ((firstCheckIn != null && lastCheckOut == null) || (firstCheckIn == null && lastCheckOut != null) || (punches.size() == 1)) {
                status = AttendanceRecord.AttendanceStatus.INCOMPLETE;
            } else if (approvedPermission != null) {
                status = AttendanceRecord.AttendanceStatus.PERMISSION;
            } else if (lateArrival && earlyDeparture) {
                status = AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT;
            } else if (lateArrival) {
                status = AttendanceRecord.AttendanceStatus.LATE;
            } else if (earlyDeparture) {
                status = AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT;
            } else if (overtimeHours > 0) {
                status = AttendanceRecord.AttendanceStatus.OVERTIME;
            } else {
                status = AttendanceRecord.AttendanceStatus.PRESENT;
            }
        }

        return AttendanceRecord.builder()
                .employeeId(employeeId)
                .employeeCode(employeeCode)
                .employeeName(employeeName)
                .department(department)
                .date(date)
                .checkInTime(firstCheckIn)
                .checkOutTime(lastCheckOut)
                .expectedWorkingHours(expectedHours)
                .totalWorkingHours(totalWorkingHours)
                .breakDurationMinutes(breakMinutes)
                .lateArrival(lateArrival)
                .lateMinutes(lateMinutes)
                .earlyDeparture(earlyDeparture)
                .earlyMinutes(earlyMinutes)
                .overtimeHours(overtimeHours)
                .status(status)
                .sourceOfPunch(mainSource)
                .punches(punches)
                .build();
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (Exception e) {
            return fallback;
        }
    }
}

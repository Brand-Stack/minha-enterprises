package com.app.billing.service;

import com.app.billing.dao.HolidayRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.AttendancePunch;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.LeaveRequest;
import com.app.billing.model.PermissionRequest;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dao.PermissionRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AttendanceCalculationEngine {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final HolidayRepository holidayRepository;
    private final CompanySettingsService companySettingsService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PermissionRequestRepository permissionRequestRepository;

    public AttendanceCalculationEngine() {
        this(null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceCalculationEngine(
            HolidayRepository holidayRepository,
            CompanySettingsService companySettingsService,
            LeaveRequestRepository leaveRequestRepository,
            PermissionRequestRepository permissionRequestRepository) {
        this.holidayRepository = holidayRepository;
        this.companySettingsService = companySettingsService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.permissionRequestRepository = permissionRequestRepository;
    }

    public AttendanceCalculationEngine(HolidayRepository holidayRepository) {
        this(holidayRepository, null, null, null);
    }

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

        if (settings == null && companySettingsService != null) {
            settings = companySettingsService.getSettings();
        }

        if (approvedLeave == null && leaveRequestRepository != null && employeeId != null) {
            approvedLeave = leaveRequestRepository
                    .findByEmployeeIdAndStatus(employeeId, LeaveRequest.LeaveStatus.APPROVED)
                    .stream()
                    .filter(l -> l.getFromDate() != null && l.getToDate() != null
                            && !date.isBefore(l.getFromDate()) && !date.isAfter(l.getToDate()))
                    .findFirst().orElse(null);
        }

        if (approvedPermission == null && permissionRequestRepository != null && employeeId != null) {
            approvedPermission = permissionRequestRepository
                    .findByEmployeeIdAndDateAndStatus(employeeId, date, PermissionRequest.PermissionStatus.APPROVED)
                    .orElse(null);
        }

        boolean isHoliday = holidayRepository != null
                ? holidayRepository.findByHolidayDate(date).map(h -> h.getActive() == null || Boolean.TRUE.equals(h.getActive())).orElse(false)
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

        if (settings == null && companySettingsService != null) {
            settings = companySettingsService.getSettings();
        }

        double expectedHours = (settings != null && settings.getWorkingHoursPerDay() != null && settings.getWorkingHoursPerDay() > 0)
                ? settings.getWorkingHoursPerDay() : 8.0;
        int breakMinutes = (settings != null && settings.getBreakDurationMinutes() != null && settings.getBreakDurationMinutes() >= 0)
                ? settings.getBreakDurationMinutes() : 45;
        int lateGraceMinutes = (settings != null && settings.getLateGracePeriodMinutes() != null && settings.getLateGracePeriodMinutes() >= 0)
                ? settings.getLateGracePeriodMinutes() : 15;
        int earlyGraceMinutes = (settings != null && settings.getEarlyCheckoutGracePeriodMinutes() != null && settings.getEarlyCheckoutGracePeriodMinutes() >= 0)
                ? settings.getEarlyCheckoutGracePeriodMinutes() : 15;
        int overtimeThreshold = (settings != null && settings.getOvertimeThresholdMinutes() != null && settings.getOvertimeThresholdMinutes() >= 0)
                ? settings.getOvertimeThresholdMinutes() : 30;

        LocalTime expectedStart = parseTime(settings != null ? settings.getWorkingStartTime() : null, LocalTime.of(10, 0));
        LocalTime expectedEnd = parseTime(settings != null ? settings.getWorkingEndTime() : null, LocalTime.of(18, 0));

        AttendanceRecord.AttendanceStatus status = AttendanceRecord.AttendanceStatus.PRESENT;
        AttendancePunch.PunchSource mainSource = AttendancePunch.PunchSource.MANUAL;

        // Precedence: Holiday (H) > Week Off (WO) > Approved Leave (LEAVE) > Present (P) / Absent (A)
        String dayOfWeek = date.getDayOfWeek().name();
        Set<String> weeklyOffDays = new HashSet<>();
        if (settings != null && settings.getWeekendDays() != null && !settings.getWeekendDays().isEmpty()) {
            settings.getWeekendDays().forEach(d -> {
                if (d != null && !d.isBlank()) weeklyOffDays.add(d.trim().toUpperCase());
            });
        } else if (settings != null && settings.getWorkingDays() != null && !settings.getWorkingDays().isEmpty()) {
            Set<String> workingDays = settings.getWorkingDays().stream()
                    .filter(d -> d != null && !d.isBlank())
                    .map(d -> d.trim().toUpperCase())
                    .collect(Collectors.toSet());
            for (java.time.DayOfWeek dow : java.time.DayOfWeek.values()) {
                if (!workingDays.contains(dow.name())) {
                    weeklyOffDays.add(dow.name());
                }
            }
        } else {
            weeklyOffDays.add("SUNDAY");
        }
        boolean isWeekend = weeklyOffDays.contains(dayOfWeek);

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
                    && status != AttendanceRecord.AttendanceStatus.HALF_DAY
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
        List<AttendancePunch> sortedPunches = new ArrayList<>(punches);
        sortedPunches.sort(Comparator.comparing(AttendancePunch::getTimestamp));
        mainSource = sortedPunches.get(0).getSource() != null ? sortedPunches.get(0).getSource() : AttendancePunch.PunchSource.MANUAL;

        LocalDateTime firstCheckIn = null;
        LocalDateTime lastCheckOut = null;

        // Find first IN punch
        for (AttendancePunch p : sortedPunches) {
            if (p.getType() == AttendancePunch.PunchType.IN) {
                firstCheckIn = p.getTimestamp();
                break;
            }
        }
        if (firstCheckIn == null && sortedPunches.get(0).getType() != AttendancePunch.PunchType.OUT) {
            firstCheckIn = sortedPunches.get(0).getTimestamp();
        }

        // Find last OUT punch
        for (int i = sortedPunches.size() - 1; i >= 0; i--) {
            AttendancePunch p = sortedPunches.get(i);
            if (p.getType() == AttendancePunch.PunchType.OUT) {
                lastCheckOut = p.getTimestamp();
                break;
            }
        }
        if (lastCheckOut == null && sortedPunches.size() > 1) {
            lastCheckOut = sortedPunches.get(sortedPunches.size() - 1).getTimestamp();
        }

        // Handle shift boundaries and overnight shifts
        LocalDateTime shiftStart = LocalDateTime.of(date, expectedStart);
        boolean isOvernight = expectedEnd.isBefore(expectedStart);
        LocalDateTime shiftEnd = isOvernight ? LocalDateTime.of(date.plusDays(1), expectedEnd) : LocalDateTime.of(date, expectedEnd);
        LocalDateTime maxAllowedStart = shiftStart.plusMinutes(lateGraceMinutes);
        LocalDateTime minAllowedEnd = shiftEnd.minusMinutes(earlyGraceMinutes);

        // Overnight punch date alignment: if checkout is on same day as checkin but time is earlier, it was next day
        if (isOvernight && firstCheckIn != null && lastCheckOut != null && lastCheckOut.isBefore(firstCheckIn)) {
            lastCheckOut = lastCheckOut.plusDays(1);
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
            if (firstCheckIn.isAfter(maxAllowedStart)) {
                lateMinutes = (int) Math.max(0, Duration.between(shiftStart, firstCheckIn).toMinutes());
                if (approvedPermission != null && approvedPermission.getEndTime() != null) {
                    LocalDateTime permEnd = LocalDateTime.of(date, approvedPermission.getEndTime());
                    if (!firstCheckIn.isAfter(permEnd)) {
                        lateArrival = false;
                        lateMinutes = 0;
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
            if (lastCheckOut.isBefore(minAllowedEnd)) {
                earlyMinutes = (int) Math.max(0, Duration.between(lastCheckOut, shiftEnd).toMinutes());
                if (approvedPermission != null && approvedPermission.getStartTime() != null) {
                    LocalDateTime permStart = LocalDateTime.of(date, approvedPermission.getStartTime());
                    if (!lastCheckOut.isBefore(permStart)) {
                        earlyDeparture = false;
                        earlyMinutes = 0;
                    } else {
                        earlyDeparture = true;
                    }
                } else {
                    earlyDeparture = true;
                }
            }
        }

        // Evaluate Overtime
        double otHoursFromShiftEnd = 0.0;
        if (lastCheckOut != null) {
            LocalDateTime otThresholdTime = shiftEnd.plusMinutes(overtimeThreshold);
            if (lastCheckOut.isAfter(otThresholdTime)) {
                long otMinutes = Duration.between(shiftEnd, lastCheckOut).toMinutes();
                otHoursFromShiftEnd = Math.round((otMinutes / 60.0) * 100.0) / 100.0;
            }
        }

        double otHoursFromWorkingHours = 0.0;
        if (totalWorkingHours > expectedHours) {
            double excessHours = totalWorkingHours - expectedHours;
            long excessMinutes = Math.round(excessHours * 60.0);
            if (excessMinutes >= overtimeThreshold) {
                otHoursFromWorkingHours = Math.round(excessHours * 100.0) / 100.0;
            }
        }

        double overtimeHours = Math.max(otHoursFromShiftEnd, otHoursFromWorkingHours);

        // Determine Status if not already HOLIDAY, WEEK_OFF, LEAVE, or HALF_DAY
        if (status != AttendanceRecord.AttendanceStatus.LEAVE
                && status != AttendanceRecord.AttendanceStatus.HALF_DAY
                && status != AttendanceRecord.AttendanceStatus.HOLIDAY
                && status != AttendanceRecord.AttendanceStatus.WEEK_OFF) {
            if (firstCheckIn == null || lastCheckOut == null || sortedPunches.size() == 1) {
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
                .punches(sortedPunches)
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

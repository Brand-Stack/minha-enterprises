package com.app.billing.controller;

import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AttendancePunch;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.Employee;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AttendanceCalculationEngine;
import com.app.billing.service.AttendanceReportService;
import com.app.billing.service.AuditService;
import com.app.billing.service.CompanySettingsService;
import com.app.billing.service.PermissionEvaluatorService;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dao.PermissionRequestRepository;
import com.app.billing.model.LeaveRequest;
import com.app.billing.model.PermissionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance Management", description = "Employee Attendance, Punch and Daily Calculations")
public class AttendanceController {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanySettingsService companySettingsService;
    private final AttendanceCalculationEngine calculationEngine;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AttendanceReportService reportService;
    private final AuditService auditService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PermissionRequestRepository permissionRequestRepository;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @GetMapping("/paged")
    @RequiresPermission(module = Modules.MASTER_ATTENDANCE, action = Modules.VIEW)
    @Operation(summary = "Paged Daily Attendance List", description = "Retrieve paginated daily attendance records for organization with filters")
    public ResponseEntity<Map<String, Object>> getPagedDailyAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : start;

        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("date").gte(start).lte(end));

        if (employeeId != null && !employeeId.isBlank()) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("employeeId").is(employeeId.trim()));
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            String cleanStatus = status.trim().toUpperCase();
            if ("EARLY_CHECKOUT".equals(cleanStatus)) {
                query.addCriteria(new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                        org.springframework.data.mongodb.core.query.Criteria.where("status").in(
                                AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT,
                                AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                        ),
                        org.springframework.data.mongodb.core.query.Criteria.where("earlyDeparture").is(true)
                ));
            } else if ("LATE".equals(cleanStatus)) {
                query.addCriteria(new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                        org.springframework.data.mongodb.core.query.Criteria.where("status").in(
                                AttendanceRecord.AttendanceStatus.LATE,
                                AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                        ),
                        org.springframework.data.mongodb.core.query.Criteria.where("lateArrival").is(true)
                ));
            } else {
                try {
                    AttendanceRecord.AttendanceStatus enumStatus = AttendanceRecord.AttendanceStatus.valueOf(cleanStatus);
                    query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("status").is(enumStatus));
                } catch (Exception ignored) {
                }
            }
        }

        if (search != null && !search.isBlank()) {
            String s = search.trim();
            org.springframework.data.mongodb.core.query.Criteria searchCriteria = new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                    org.springframework.data.mongodb.core.query.Criteria.where("employeeName").regex(s, "i"),
                    org.springframework.data.mongodb.core.query.Criteria.where("employeeCode").regex(s, "i")
            );
            query.addCriteria(searchCriteria);
        }

        List<AttendanceRecord> allMatching = mongoTemplate.find(query, AttendanceRecord.class);
        allMatching.sort(java.util.Comparator.comparing(AttendanceRecord::getDate).reversed()
                .thenComparing(r -> r.getEmployeeName() != null ? r.getEmployeeName() : ""));

        long total = allMatching.size();
        long presentCount = allMatching.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.OVERTIME
                || r.getStatus() == AttendanceRecord.AttendanceStatus.WORK_FROM_HOME
                || r.getStatus() == AttendanceRecord.AttendanceStatus.PERMISSION).count();
        long absentCount = allMatching.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT).count();
        long lateCount = allMatching.stream().filter(r -> Boolean.TRUE.equals(r.getLateArrival()) || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT).count();

        int pageSize = size > 0 ? size : 10;
        int totalPages = (int) Math.ceil((double) total / pageSize);

        int startIdx = Math.min(page * pageSize, (int) total);
        int endIdx = Math.min(startIdx + pageSize, (int) total);
        List<AttendanceRecord> pagedList = (startIdx <= total) ? allMatching.subList(startIdx, endIdx) : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("content", pagedList);
        result.put("totalElements", total);
        result.put("totalPages", totalPages);
        result.put("number", page);
        result.put("size", pageSize);
        result.put("presentCount", presentCount);
        result.put("absentCount", absentCount);
        result.put("lateCount", lateCount);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/punch")
    @Operation(summary = "Punch In or Out", description = "Employee punch check-in or check-out with optional geofencing validation")
    public ResponseEntity<AttendanceRecord> punch(@RequestBody PunchRequest request) {
        Employee caller = permissionEvaluatorService.currentEmployee();
        if (caller == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        Employee targetEmployee = caller;
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        CompanySettingsDto settings = companySettingsService.getSettings();

        AttendancePunch.PunchType type = "OUT".equalsIgnoreCase(request.getPunchType()) ? AttendancePunch.PunchType.OUT : AttendancePunch.PunchType.IN;
        String actionLabel = (type == AttendancePunch.PunchType.IN) ? "Punch In" : "Punch Out";

        // Geofencing Check for Employees
        Double distanceMeters = null;
        if (settings.getOfficeLatitude() != null && settings.getOfficeLongitude() != null) {
            if (request.getLatitude() != null && request.getLongitude() != null) {
                distanceMeters = calculateDistanceMeters(
                        settings.getOfficeLatitude(),
                        settings.getOfficeLongitude(),
                        request.getLatitude(),
                        request.getLongitude()
                );
            }

            if (!permissionEvaluatorService.isCurrentUserAdmin()) {
                if (request.getLatitude() == null || request.getLongitude() == null) {
                    throw new IllegalArgumentException("Location permission is required to " + actionLabel + ".");
                }
                double allowedRadius = settings.getAllowedRadiusMeters() != null ? settings.getAllowedRadiusMeters() : 100.0;
                if (distanceMeters == null || distanceMeters > allowedRadius) {
                    throw new IllegalArgumentException("You are outside the permitted attendance location.");
                }
            }
        }

        AttendancePunch newPunch = AttendancePunch.builder()
                .punchId("PUNCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .timestamp(now)
                .type(type)
                .source(AttendancePunch.PunchSource.MANUAL)
                .deviceId("WEB_APP")
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .distanceFromOfficeMeters(distanceMeters)
                .processed(true)
                .build();

        Optional<AttendanceRecord> recordOpt = attendanceRecordRepository.findByEmployeeIdAndDate(targetEmployee.getId(), today);
        AttendanceRecord record;
        List<AttendancePunch> punches = new ArrayList<>();

        if (recordOpt.isPresent()) {
            record = recordOpt.get();
            if (type == AttendancePunch.PunchType.IN && record.getCheckInTime() != null && record.getCheckOutTime() == null) {
                throw new IllegalArgumentException("You are already checked in for today.");
            }
            if (type == AttendancePunch.PunchType.IN && record.getCheckInTime() != null && record.getCheckOutTime() != null) {
                throw new IllegalArgumentException("You have already completed punch in and punch out for today.");
            }
            if (type == AttendancePunch.PunchType.OUT && record.getCheckInTime() == null) {
                throw new IllegalArgumentException("You must punch in before punching out.");
            }
            if (type == AttendancePunch.PunchType.OUT && record.getCheckOutTime() != null) {
                throw new IllegalArgumentException("You are already checked out for today.");
            }
            if (record.getPunches() != null) {
                punches.addAll(record.getPunches());
            }
        } else {
            if (type == AttendancePunch.PunchType.OUT) {
                throw new IllegalArgumentException("You must punch in before punching out.");
            }
            record = AttendanceRecord.builder()
                    .employeeId(targetEmployee.getId())
                    .employeeCode(targetEmployee.getEmployeeCode())
                    .employeeName(targetEmployee.getEmployeeName())
                    .department(targetEmployee.getCategory())
                    .date(today)
                    .build();
        }

        // Duplicate punch check: prevent accidental duplicate punch within 60 seconds
        if (!punches.isEmpty()) {
            AttendancePunch lastPunch = punches.get(punches.size() - 1);
            long diffSeconds = java.time.Duration.between(lastPunch.getTimestamp(), now).getSeconds();
            if (diffSeconds < 60 && lastPunch.getType() == type) {
                log.warn("Ignoring duplicate punch for employee {} within {}s", targetEmployee.getId(), diffSeconds);
                return ResponseEntity.ok(record);
            }
        }

        punches.add(newPunch);

        AttendanceRecord updated = calculationEngine.calculateDailyAttendance(
                targetEmployee.getId(),
                targetEmployee.getEmployeeCode(),
                targetEmployee.getEmployeeName(),
                targetEmployee.getCategory(),
                today,
                punches,
                settings,
                null,
                null
        );

        if (record.getId() != null) {
            updated.setId(record.getId());
        }

        AttendanceRecord saved = attendanceRecordRepository.save(updated);
        String auditMsg = "Employee " + targetEmployee.getEmployeeName() + " punched " + type;
        auditService.log("PUNCH_" + type, Modules.MY_ATTENDANCE, saved.getId(), auditMsg);
        return ResponseEntity.ok(saved);
    }

    private static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @GetMapping("/my-attendance")
    @RequiresPermission(module = Modules.MY_ATTENDANCE, action = Modules.VIEW)
    @Operation(summary = "My Attendance", description = "Retrieve logged-in employee's attendance records")
    public ResponseEntity<List<AttendanceRecord>> getMyAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Employee current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        LocalDate start;
        LocalDate end;

        if (startDate == null && endDate == null) {
            start = LocalDate.now();
            end = LocalDate.now();
        } else if (startDate != null && endDate == null) {
            start = startDate;
            end = startDate;
        } else if (startDate == null && endDate != null) {
            start = endDate;
            end = endDate;
        } else {
            start = startDate;
            end = endDate;
        }

        List<AttendanceRecord> records;
        if (start.equals(end)) {
            records = attendanceRecordRepository.findByEmployeeIdAndDate(current.getId(), start)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            records = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(current.getId(), start, end);
        }

        return ResponseEntity.ok(records);
    }

    @GetMapping("/daily")
    @RequiresPermission(module = Modules.MASTER_ATTENDANCE, action = Modules.VIEW)
    @Operation(summary = "Daily Attendance List", description = "Retrieve organization attendance records for date range (Admin/HR)")
    public ResponseEntity<List<AttendanceRecord>> getDailyAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeId) {
        LocalDate start;
        LocalDate end;

        if (startDate == null && endDate == null) {
            start = (date != null) ? date : LocalDate.now();
            end = start;
        } else if (startDate != null && endDate == null) {
            start = startDate;
            end = startDate;
        } else if (startDate == null && endDate != null) {
            start = endDate;
            end = endDate;
        } else {
            start = startDate;
            end = endDate;
        }

        List<AttendanceRecord> records;
        if (employeeId != null && !employeeId.trim().isEmpty()) {
            if (start.equals(end)) {
                records = attendanceRecordRepository.findByEmployeeIdAndDate(employeeId.trim(), start)
                        .map(List::of)
                        .orElse(List.of());
            } else {
                records = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId.trim(), start, end);
            }
        } else {
            if (start.equals(end)) {
                records = attendanceRecordRepository.findByDate(start);
            } else {
                records = attendanceRecordRepository.findByDateBetweenOrderByDateAsc(start, end);
            }
        }

        return ResponseEntity.ok(records);
    }

    @RequestMapping(value = "/update", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermission(module = Modules.MASTER_ATTENDANCE, action = "CORRECT_ATTENDANCE")
    @Operation(summary = "Admin Update Attendance", description = "Admin location-independent update/create of employee attendance record with detailed audit trail")
    public ResponseEntity<AttendanceRecord> updateAttendance(@RequestBody AdminAttendanceUpdateRequest request) {
        Employee current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        if (request.getEmployeeId() == null || request.getEmployeeId().trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID is required");
        }
        if (request.getDate() == null) {
            throw new IllegalArgumentException("Attendance date is required");
        }

        // Validation: Punch Out cannot be before Punch In
        if (request.getCheckInTime() != null && request.getCheckOutTime() != null) {
            if (request.getCheckOutTime().isBefore(request.getCheckInTime())) {
                throw new IllegalArgumentException("Punch Out time cannot be before Punch In time.");
            }
        }

        AttendanceRecord record = null;
        if (request.getId() != null && !request.getId().trim().isEmpty()) {
            record = attendanceRecordRepository.findById(request.getId()).orElse(null);
        }
        if (record == null) {
            record = attendanceRecordRepository.findByEmployeeIdAndDate(request.getEmployeeId(), request.getDate()).orElse(null);
        }

        Employee targetEmp = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + request.getEmployeeId()));

        LocalDateTime prevCheckIn = record != null ? record.getCheckInTime() : null;
        LocalDateTime prevCheckOut = record != null ? record.getCheckOutTime() : null;

        if (record == null) {
            record = AttendanceRecord.builder()
                    .employeeId(targetEmp.getId())
                    .employeeCode(targetEmp.getEmployeeCode())
                    .employeeName(targetEmp.getEmployeeName())
                    .department(targetEmp.getCategory())
                    .date(request.getDate())
                    .build();
        }

        record.setCheckInTime(request.getCheckInTime());
        record.setCheckOutTime(request.getCheckOutTime());
        if (request.getStatus() != null) {
            record.setStatus(request.getStatus());
        }
        if (request.getRemarks() != null && !request.getRemarks().trim().isEmpty()) {
            record.setRemarks(request.getRemarks());
        }

        record.setManuallyCorrected(true);
        record.setCorrectedBy(current.getEmployeeName() + " (" + current.getEmail() + ")");
        record.setCorrectedAt(LocalDateTime.now());
        record.setCorrectionReason(request.getReason() != null ? request.getReason() : request.getRemarks());
        record.setSourceOfPunch(AttendancePunch.PunchSource.MANUAL);

        List<AttendancePunch> punches = new ArrayList<>();
        if (request.getCheckInTime() != null) {
            punches.add(AttendancePunch.builder()
                    .punchId("PUNCH-IN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .timestamp(request.getCheckInTime())
                    .type(AttendancePunch.PunchType.IN)
                    .source(AttendancePunch.PunchSource.MANUAL)
                    .punchedByAdmin(true)
                    .adminEmployeeId(current.getId())
                    .build());
        }
        if (request.getCheckOutTime() != null) {
            punches.add(AttendancePunch.builder()
                    .punchId("PUNCH-OUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .timestamp(request.getCheckOutTime())
                    .type(AttendancePunch.PunchType.OUT)
                    .source(AttendancePunch.PunchSource.MANUAL)
                    .punchedByAdmin(true)
                    .adminEmployeeId(current.getId())
                    .build());
        }
        if (punches.isEmpty() && record != null && record.getPunches() != null) {
            punches.addAll(record.getPunches());
        }

        LeaveRequest approvedLeave = leaveRequestRepository
                .findByEmployeeIdAndStatus(targetEmp.getId(), LeaveRequest.LeaveStatus.APPROVED)
                .stream()
                .filter(l -> (l.getFromDate().isBefore(request.getDate()) || l.getFromDate().isEqual(request.getDate()))
                        && (l.getToDate().isAfter(request.getDate()) || l.getToDate().isEqual(request.getDate())))
                .findFirst().orElse(null);

        PermissionRequest approvedPermission = permissionRequestRepository
                .findByEmployeeIdAndDateAndStatus(targetEmp.getId(), request.getDate(), PermissionRequest.PermissionStatus.APPROVED)
                .orElse(null);

        CompanySettingsDto settings = companySettingsService.getSettings();
        AttendanceRecord recalculated = calculationEngine.calculateDailyAttendance(
                targetEmp.getId(),
                targetEmp.getEmployeeCode(),
                targetEmp.getEmployeeName(),
                targetEmp.getCategory(),
                request.getDate(),
                punches,
                settings,
                approvedLeave,
                approvedPermission
        );

        if (record != null && record.getId() != null) {
            recalculated.setId(record.getId());
        }
        recalculated.setCheckInTime(request.getCheckInTime());
        recalculated.setCheckOutTime(request.getCheckOutTime());
        recalculated.setManuallyCorrected(true);
        recalculated.setCorrectedBy(current.getEmployeeName() + " (" + current.getEmail() + ")");
        recalculated.setCorrectedAt(LocalDateTime.now());
        recalculated.setCorrectionReason(request.getReason() != null ? request.getReason() : request.getRemarks());
        recalculated.setSourceOfPunch(AttendancePunch.PunchSource.MANUAL);

        if (request.getStatus() != null && request.getCheckInTime() == null && request.getCheckOutTime() == null) {
            recalculated.setStatus(request.getStatus());
        }
        if (request.getRemarks() != null) {
            recalculated.setRemarks(request.getRemarks());
        }

        AttendanceRecord saved = attendanceRecordRepository.save(recalculated);

        String auditMsg = String.format("Employee: %s (ID: %s) | Attendance Date: %s | Previous Punch In: %s | New Punch In: %s | Previous Punch Out: %s | New Punch Out: %s | Updated By: %s | Updated Date/Time: %s | Reason/Remarks: %s | Source: Admin Update",
                saved.getEmployeeName(),
                saved.getEmployeeId(),
                saved.getDate(),
                prevCheckIn != null ? prevCheckIn.toString() : "Missing",
                saved.getCheckInTime() != null ? saved.getCheckInTime().toString() : "Missing",
                prevCheckOut != null ? prevCheckOut.toString() : "Missing",
                saved.getCheckOutTime() != null ? saved.getCheckOutTime().toString() : "Missing",
                current.getEmployeeName(),
                LocalDateTime.now(),
                saved.getCorrectionReason() != null ? saved.getCorrectionReason() : "Admin Update"
        );
        auditService.log("ADMIN_ATTENDANCE_UPDATE", Modules.MASTER_ATTENDANCE, saved.getId(), auditMsg);

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/correct/{id}")
    @RequiresPermission(module = Modules.MASTER_ATTENDANCE, action = "CORRECT_ATTENDANCE")
    @Operation(summary = "Manual Attendance Correction", description = "Admin manual override of attendance record with audit trail")
    public ResponseEntity<AttendanceRecord> correctAttendance(
            @PathVariable String id,
            @RequestBody CorrectionRequest request) {
        AttendanceRecord existing = attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found: " + id));

        AdminAttendanceUpdateRequest updateReq = new AdminAttendanceUpdateRequest();
        updateReq.setId(id);
        updateReq.setEmployeeId(existing.getEmployeeId());
        updateReq.setDate(existing.getDate());
        if (request.getCheckInTime() != null) {
            updateReq.setCheckInTime(LocalDateTime.of(existing.getDate(), request.getCheckInTime()));
        } else {
            updateReq.setCheckInTime(existing.getCheckInTime());
        }
        if (request.getCheckOutTime() != null) {
            updateReq.setCheckOutTime(LocalDateTime.of(existing.getDate(), request.getCheckOutTime()));
        } else {
            updateReq.setCheckOutTime(existing.getCheckOutTime());
        }
        updateReq.setStatus(request.getStatus());
        updateReq.setRemarks(request.getRemarks());
        updateReq.setReason(request.getReason() != null ? request.getReason() : request.getRemarks());

        return updateAttendance(updateReq);
    }

    @GetMapping("/dashboard-kpis")
    @RequiresPermission(module = Modules.ATTENDANCE, action = Modules.VIEW)
    @Operation(summary = "Attendance Dashboard KPIs", description = "Retrieve organization-wide attendance KPIs for a given date or range")
    public ResponseEntity<Map<String, Object>> getDashboardKpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String employeeId) {
        
        LocalDate start;
        LocalDate end;

        if (startDate == null && endDate == null) {
            start = (date != null) ? date : LocalDate.now();
            end = start;
        } else if (startDate != null && endDate == null) {
            start = startDate;
            end = startDate;
        } else if (startDate == null && endDate != null) {
            start = endDate;
            end = endDate;
        } else {
            start = startDate;
            end = endDate;
        }

        Employee caller = permissionEvaluatorService.currentEmployee();
        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        String targetEmpId = (!isAdmin && caller != null) ? caller.getId() : employeeId;

        long totalEmployees = employeeRepository.count();
        List<AttendanceRecord> records;
        if (start.equals(end)) {
            records = attendanceRecordRepository.findByDate(start);
        } else {
            records = attendanceRecordRepository.findByDateBetween(start, end);
        }

        if (targetEmpId != null && !targetEmpId.trim().isEmpty()) {
            records = records.stream().filter(r -> targetEmpId.equalsIgnoreCase(r.getEmployeeId())).toList();
        }
        if (department != null && !department.trim().isEmpty()) {
            records = records.stream().filter(r -> r.getDepartment() != null && r.getDepartment().equalsIgnoreCase(department.trim())).toList();
        }

        long presentCount = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE
                || r.getStatus() == AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.PERMISSION
                || r.getStatus() == AttendanceRecord.AttendanceStatus.WORK_FROM_HOME
                || r.getStatus() == AttendanceRecord.AttendanceStatus.OVERTIME).count();

        long absentCount = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT).count();
        long onLeaveCount = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE || r.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY).count();
        long lateCount = records.stream().filter(r -> Boolean.TRUE.equals(r.getLateArrival())).count();
        long earlyCheckoutCount = records.stream().filter(r -> Boolean.TRUE.equals(r.getEarlyDeparture())).count();
        long checkedInCount = records.stream().filter(r -> r.getCheckInTime() != null && r.getCheckOutTime() == null).count();
        long overtimeCount = records.stream().filter(r -> r.getOvertimeHours() != null && r.getOvertimeHours() > 0).count();

        double totalOvertimeHours = records.stream().mapToDouble(r -> r.getOvertimeHours() != null ? r.getOvertimeHours() : 0.0).sum();
        double attendancePercentage = totalEmployees > 0 ? (presentCount * 100.0 / totalEmployees) : 0.0;

        Map<String, Object> map = new HashMap<>();
        map.put("startDate", start);
        map.put("endDate", end);
        map.put("date", start.equals(end) ? start : null);
        map.put("totalEmployees", totalEmployees);
        map.put("presentToday", presentCount);
        map.put("absentToday", absentCount);
        map.put("onLeave", onLeaveCount);
        map.put("lateEmployees", lateCount);
        map.put("earlyCheckout", earlyCheckoutCount);
        map.put("currentlyCheckedIn", checkedInCount);
        map.put("overtimeEmployees", overtimeCount);
        map.put("totalOvertimeHours", Math.round(totalOvertimeHours * 100.0) / 100.0);
        map.put("attendancePercentage", Math.round(attendancePercentage * 10.0) / 10.0);

        return ResponseEntity.ok(map);
    }

    @GetMapping("/kpi-details")
    @RequiresPermission(module = Modules.ATTENDANCE, action = Modules.VIEW)
    @Operation(summary = "Attendance KPI Modal Details", description = "Retrieve filtered employee records for KPI detail popups")
    public ResponseEntity<List<AttendanceRecord>> getKpiDetails(
            @RequestParam(required = false) String category, // PRESENT, ABSENT, LEAVE, LATE, OVERTIME
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String employeeId) {
        
        Employee caller = permissionEvaluatorService.currentEmployee();
        if (caller == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        // Server-side security check: Non-admin employees CANNOT view other employees' attendance details
        String targetEmpId = !isAdmin ? caller.getId() : employeeId;

        LocalDate start;
        LocalDate end;

        if (startDate == null && endDate == null) {
            start = LocalDate.now();
            end = LocalDate.now();
        } else if (startDate != null && endDate == null) {
            start = startDate;
            end = startDate;
        } else if (startDate == null && endDate != null) {
            start = endDate;
            end = endDate;
        } else {
            start = startDate;
            end = endDate;
        }

        List<AttendanceRecord> records;
        if (start.equals(end)) {
            records = attendanceRecordRepository.findByDate(start);
        } else {
            records = attendanceRecordRepository.findByDateBetween(start, end);
        }

        if (targetEmpId != null && !targetEmpId.trim().isEmpty()) {
            String filterId = targetEmpId.trim();
            records = records.stream().filter(r -> filterId.equalsIgnoreCase(r.getEmployeeId())).toList();
        }
        if (department != null && !department.trim().isEmpty()) {
            String dept = department.trim();
            records = records.stream().filter(r -> r.getDepartment() != null && r.getDepartment().equalsIgnoreCase(dept)).toList();
        }

        if (category != null && !category.trim().isEmpty()) {
            String catUpper = category.trim().toUpperCase();
            switch (catUpper) {
                case "PRESENT":
                    records = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                            || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE
                            || r.getStatus() == AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT
                            || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                            || r.getStatus() == AttendanceRecord.AttendanceStatus.PERMISSION
                            || r.getStatus() == AttendanceRecord.AttendanceStatus.WORK_FROM_HOME
                            || r.getStatus() == AttendanceRecord.AttendanceStatus.OVERTIME).toList();
                    break;
                case "ABSENT":
                    records = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT).toList();
                    break;
                case "LEAVE":
                    records = records.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE || r.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY).toList();
                    break;
                case "LATE":
                    records = records.stream().filter(r -> Boolean.TRUE.equals(r.getLateArrival())).toList();
                    break;
                case "OVERTIME":
                    records = records.stream().filter(r -> r.getOvertimeHours() != null && r.getOvertimeHours() > 0).toList();
                    break;
            }
        }

        return ResponseEntity.ok(records);
    }

    @GetMapping("/my-dashboard-kpis")
    @Operation(summary = "My Attendance Dashboard KPIs", description = "Retrieve logged-in employee's dashboard metrics")
    public ResponseEntity<Map<String, Object>> getMyDashboardKpis() {
        Employee current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        Optional<AttendanceRecord> todayRecordOpt = attendanceRecordRepository.findByEmployeeIdAndDate(current.getId(), today);
        List<AttendanceRecord> monthRecords = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(current.getId(), firstOfMonth, today);

        long presentDays = monthRecords.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE
                || r.getStatus() == AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                || r.getStatus() == AttendanceRecord.AttendanceStatus.PERMISSION
                || r.getStatus() == AttendanceRecord.AttendanceStatus.WORK_FROM_HOME
                || r.getStatus() == AttendanceRecord.AttendanceStatus.OVERTIME).count();

        long leaveDays = monthRecords.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE || r.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY).count();
        double overtimeHours = monthRecords.stream().mapToDouble(r -> r.getOvertimeHours() != null ? r.getOvertimeHours() : 0.0).sum();

        Map<String, Object> map = new HashMap<>();
        map.put("employeeName", current.getEmployeeName());
        map.put("todayStatus", todayRecordOpt.map(AttendanceRecord::getStatus).orElse(AttendanceRecord.AttendanceStatus.ABSENT));
        map.put("checkInTime", todayRecordOpt.map(AttendanceRecord::getCheckInTime).orElse(null));
        map.put("checkOutTime", todayRecordOpt.map(AttendanceRecord::getCheckOutTime).orElse(null));
        map.put("todayWorkingHours", todayRecordOpt.map(AttendanceRecord::getTotalWorkingHours).orElse(0.0));
        map.put("monthPresentDays", presentDays);
        map.put("monthLeaveDays", leaveDays);
        map.put("monthOvertimeHours", Math.round(overtimeHours * 100.0) / 100.0);

        return ResponseEntity.ok(map);
    }

    // --- EXPORT ENDPOINTS ---

    @GetMapping("/my-attendance/export/excel")
    @Operation(summary = "Export My Attendance Excel")
    public ResponseEntity<byte[]> exportMyAttendanceExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws java.io.IOException {
        Employee current = permissionEvaluatorService.currentEmployee();
        if (current == null) throw new AccessDeniedException("User not authenticated");
        byte[] excel = reportService.generateAttendanceReportExcel(startDate, endDate, current.getId(), null);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=My_Attendance.xlsx")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/my-attendance/export/pdf")
    @Operation(summary = "Export My Attendance PDF")
    public ResponseEntity<byte[]> exportMyAttendancePdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Employee current = permissionEvaluatorService.currentEmployee();
        if (current == null) throw new AccessDeniedException("User not authenticated");
        byte[] pdf = reportService.generateAttendanceReportPdf(startDate, endDate, current.getId(), null);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=My_Attendance.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/daily/export/excel")
    @RequiresPermission(module = Modules.ATTENDANCE, action = Modules.EXPORT)
    @Operation(summary = "Export Daily Attendance Excel")
    public ResponseEntity<byte[]> exportDailyAttendanceExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status) throws java.io.IOException {
        byte[] excel = reportService.generateAttendanceReportExcel(startDate, endDate, employeeId, status);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Daily_Attendance.xlsx")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/daily/export/pdf")
    @RequiresPermission(module = Modules.ATTENDANCE, action = Modules.EXPORT)
    @Operation(summary = "Export Daily Attendance PDF")
    public ResponseEntity<byte[]> exportDailyAttendancePdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status) {
        byte[] pdf = reportService.generateAttendanceReportPdf(startDate, endDate, employeeId, status);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Daily_Attendance.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @DeleteMapping("/my-attendance/{id}")
    @RequiresPermission(module = Modules.MY_ATTENDANCE, action = Modules.DELETE)
    @Operation(summary = "Delete My Attendance Record", description = "Delete logged in employee's attendance record")
    public ResponseEntity<Map<String, String>> deleteMyAttendance(@PathVariable String id) {
        Employee current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        AttendanceRecord record = attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + id));

        if (!permissionEvaluatorService.isCurrentUserAdmin() && !current.getId().equals(record.getEmployeeId())) {
            throw new AccessDeniedException("You are not authorized to delete another employee's attendance record.");
        }

        attendanceRecordRepository.deleteById(id);
        auditService.log("DELETE_MY_ATTENDANCE", Modules.MY_ATTENDANCE, id,
                "Deleted attendance record for date " + record.getDate() + " (Employee: " + record.getEmployeeName() + ")");

        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Attendance record deleted successfully.");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.MASTER_ATTENDANCE, action = Modules.DELETE)
    @Operation(summary = "Delete Master Attendance Record", description = "Admin / HR deletion of employee attendance record")
    public ResponseEntity<Map<String, String>> deleteAttendance(@PathVariable String id) {
        AttendanceRecord record = attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + id));

        attendanceRecordRepository.deleteById(id);
        auditService.log("DELETE_ATTENDANCE", Modules.MASTER_ATTENDANCE, id,
                "Deleted master attendance record for employee " + record.getEmployeeName() + " on date " + record.getDate());

        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Attendance record deleted successfully.");
        return ResponseEntity.ok(resp);
    }

    @Data
    public static class PunchRequest {
        private String punchType; // IN or OUT
        private Double latitude;
        private Double longitude;
        private String targetEmployeeId;
    }

    @Data
    public static class CorrectionRequest {
        private LocalTime checkInTime;
        private LocalTime checkOutTime;
        private AttendanceRecord.AttendanceStatus status;
        private String remarks;
        private String reason;
    }

    @Data
    public static class AdminAttendanceUpdateRequest {
        private String id;
        private String employeeId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime checkInTime;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime checkOutTime;
        private AttendanceRecord.AttendanceStatus status;
        private String remarks;
        private String reason;
    }
}

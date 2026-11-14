package com.app.billing.service;

import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.HolidayRepository;
import com.app.billing.dao.LeaveEntitlementRepository;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.CompanySettings;
import com.app.billing.model.Employee;
import com.app.billing.model.Holiday;
import com.app.billing.model.LeaveEntitlement;
import com.app.billing.model.LeaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveManagementService {

    private final LeaveEntitlementRepository entitlementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanySettingsService companySettingsService;
    private final HolidayRepository holidayRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public LeaveEntitlement getOrCreateEntitlement(String employeeId, Integer year) {
        int currentYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        Optional<LeaveEntitlement> entitlementOpt = entitlementRepository.findByEmployeeIdAndYear(employeeId, currentYear);

        CompanySettingsDto settings = companySettingsService != null ? companySettingsService.getSettings() : null;
        int casual = (settings != null && settings.getCasualLeaveEntitlementPerYear() != null) ? settings.getCasualLeaveEntitlementPerYear() : 12;
        int medical = (settings != null && settings.getMedicalLeaveEntitlementPerYear() != null) ? settings.getMedicalLeaveEntitlementPerYear() : 10;
        int emergency = (settings != null && settings.getEmergencyLeaveEntitlementPerYear() != null) ? settings.getEmergencyLeaveEntitlementPerYear() : 5;
        int compOff = (settings != null && settings.getCompOffEntitlementPerYear() != null) ? settings.getCompOffEntitlementPerYear() : 3;

        LeaveEntitlement entitlement;
        if (entitlementOpt.isPresent()) {
            entitlement = entitlementOpt.get();
            entitlement.setCasualLeaveTotal(casual);
            entitlement.setMedicalLeaveTotal(medical);
            entitlement.setEmergencyLeaveTotal(emergency);
            entitlement.setCompOffTotal(compOff);
            LeaveEntitlement saved = entitlementRepository.save(entitlement);
            entitlement = saved != null ? saved : entitlement;
        } else {
            Employee employee = employeeRepository.findById(employeeId)
                    .or(() -> employeeRepository.findByEmployeeCode(employeeId))
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

            LeaveEntitlement newEnt = LeaveEntitlement.builder()
                    .employeeId(employee.getId())
                    .employeeCode(employee.getEmployeeCode())
                    .employeeName(employee.getEmployeeName())
                    .year(currentYear)
                    .casualLeaveTotal(casual)
                    .casualLeaveUsed(0)
                    .medicalLeaveTotal(medical)
                    .medicalLeaveUsed(0)
                    .emergencyLeaveTotal(emergency)
                    .emergencyLeaveUsed(0)
                    .compOffTotal(compOff)
                    .compOffUsed(0)
                    .otherLeaveTotal(0)
                    .otherLeaveUsed(0)
                    .build();

            LeaveEntitlement saved = entitlementRepository.save(newEnt);
            entitlement = saved != null ? saved : newEnt;
        }

        return enrichEntitlementWithPending(entitlement);
    }

    public LeaveEntitlement enrichEntitlementWithPending(LeaveEntitlement entitlement) {
        if (entitlement == null || entitlement.getEmployeeId() == null) {
            return entitlement;
        }
        int year = entitlement.getYear() != null && entitlement.getYear() > 0 ? entitlement.getYear() : LocalDate.now().getYear();
        List<LeaveRequest> pendingList = leaveRequestRepository.findByEmployeeIdAndStatus(entitlement.getEmployeeId(), LeaveRequest.LeaveStatus.PENDING);
        double clPending = 0.0;
        double mlPending = 0.0;
        double elPending = 0.0;
        double coPending = 0.0;
        if (pendingList != null) {
            for (LeaveRequest req : pendingList) {
                if (req.getFromDate() != null && req.getFromDate().getYear() == year) {
                    double d = req.getNumberOfDays() != null ? req.getNumberOfDays() : 0.0;
                    if (req.getLeaveType() == LeaveRequest.LeaveType.CASUAL_LEAVE) {
                        clPending += d;
                    } else if (req.getLeaveType() == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
                        mlPending += d;
                    } else if (req.getLeaveType() == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
                        elPending += d;
                    } else if (req.getLeaveType() == LeaveRequest.LeaveType.COMP_OFF) {
                        coPending += d;
                    }
                }
            }
        }
        entitlement.setCasualLeavePending(clPending);
        entitlement.setMedicalLeavePending(mlPending);
        entitlement.setEmergencyLeavePending(elPending);
        entitlement.setCompOffPending(coPending);
        return entitlement;
    }

    public List<LeaveEntitlement> getAllEntitlementsForYear(Integer year) {
        int currentYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        List<Employee> employees = employeeRepository.findAll();
        List<LeaveEntitlement> list = new ArrayList<>();
        for (Employee emp : employees) {
            list.add(getOrCreateEntitlement(emp.getId(), currentYear));
        }
        return list;
    }

    /**
     * Authoritative calculation of leave days excluding weekly off days and organization holidays.
     * Working days only are counted. If a date is both weekly off and holiday, it is excluded once.
     */
    public double calculateLeaveDays(LocalDate fromDate, LocalDate toDate, Boolean isHalfDay) {
        if (fromDate == null || toDate == null) {
            return 0.0;
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("To date cannot be before From date");
        }

        CompanySettingsDto settings = companySettingsService != null ? companySettingsService.getSettings() : null;
        Set<String> weekendDays = resolveWeekendDays(settings);

        List<Holiday> holidays = holidayRepository != null
                ? holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(fromDate, toDate)
                : List.of();
        Set<LocalDate> holidayDates = holidays.stream()
                .filter(h -> h.getActive() == null || Boolean.TRUE.equals(h.getActive()))
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        long eligibleWorkingDays = 0;
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            String dayName = current.getDayOfWeek().name();
            boolean isWeekend = weekendDays.contains(dayName);
            boolean isHoliday = holidayDates.contains(current);

            if (!isWeekend && !isHoliday) {
                eligibleWorkingDays++;
            }
            current = current.plusDays(1);
        }

        if (eligibleWorkingDays == 0) {
            return 0.0;
        }

        if (Boolean.TRUE.equals(isHalfDay)) {
            return 0.5;
        }

        return (double) eligibleWorkingDays;
    }

    public List<LocalDate> getEligibleLeaveDates(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            return List.of();
        }
        CompanySettingsDto settings = companySettingsService != null ? companySettingsService.getSettings() : null;
        Set<String> weekendDays = resolveWeekendDays(settings);

        List<Holiday> holidays = holidayRepository != null
                ? holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(fromDate, toDate)
                : List.of();
        Set<LocalDate> holidayDates = holidays.stream()
                .filter(h -> h.getActive() == null || Boolean.TRUE.equals(h.getActive()))
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        List<LocalDate> eligibleDates = new ArrayList<>();
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            String dayName = current.getDayOfWeek().name();
            boolean isWeekend = weekendDays.contains(dayName);
            boolean isHoliday = holidayDates.contains(current);
            if (!isWeekend && !isHoliday) {
                eligibleDates.add(current);
            }
            current = current.plusDays(1);
        }
        return eligibleDates;
    }

    private Set<String> resolveWeekendDays(CompanySettingsDto settings) {
        Set<String> weekendDays = new HashSet<>();
        if (settings != null && settings.getWeekendDays() != null && !settings.getWeekendDays().isEmpty()) {
            settings.getWeekendDays().forEach(d -> {
                if (d != null && !d.isBlank()) weekendDays.add(d.trim().toUpperCase());
            });
        } else if (settings != null && settings.getWorkingDays() != null && !settings.getWorkingDays().isEmpty()) {
            Set<String> workingDays = settings.getWorkingDays().stream()
                    .filter(d -> d != null && !d.isBlank())
                    .map(d -> d.trim().toUpperCase())
                    .collect(Collectors.toSet());
            for (DayOfWeek dow : DayOfWeek.values()) {
                if (!workingDays.contains(dow.name())) {
                    weekendDays.add(dow.name());
                }
            }
        } else {
            weekendDays.add("SUNDAY");
        }
        return weekendDays;
    }

    @Transactional
    public LeaveRequest applyLeave(LeaveRequest request) {
        if (request.getEmployeeId() == null || request.getEmployeeId().trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID is required for leave request");
        }
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .or(() -> employeeRepository.findByEmployeeCode(request.getEmployeeId()))
                .or(() -> employeeRepository.findByLoginId(request.getEmployeeId()))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + request.getEmployeeId()));

        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("From date and To date are required for leave request");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("To date cannot be before From date");
        }

        double days = calculateLeaveDays(request.getFromDate(), request.getToDate(), request.getIsHalfDay());
        if (days <= 0.0) {
            throw new IllegalArgumentException("Selected date range contains no working days (all selected dates are weekly offs or holidays)");
        }

        request.setEmployeeId(employee.getId());
        request.setEmployeeCode(employee.getEmployeeCode());
        request.setEmployeeName(employee.getEmployeeName());
        request.setDepartment(employee.getCategory());
        request.setNumberOfDays(days);

        validateLeaveRequest(request, null);

        request.setStatus(LeaveRequest.LeaveStatus.PENDING);
        request.setAppliedDate(LocalDate.now());

        LeaveRequest saved = leaveRequestRepository.save(request);
        auditService.log("APPLY_LEAVE", "LEAVE_MGMT", saved.getId(),
                "Leave applied by " + employee.getEmployeeName() + " (" + request.getLeaveType() + ", " + days + " days)");

        // Send notification to Admin/HR approvers
        try {
            notificationService.sendNotificationToApprovers(
                    com.app.billing.security.Modules.LEAVE_MGMT,
                    "APPROVE_LEAVE",
                    com.app.billing.model.Notification.NotificationType.LEAVE_REQUEST_SUBMITTED,
                    "New Leave Request",
                    employee.getEmployeeName() + " has submitted a " + request.getLeaveType() + " request for " + request.getFromDate() + " to " + request.getToDate() + ".",
                    "LeaveRequest",
                    saved.getId(),
                    "/attendance/leaves",
                    com.app.billing.model.Notification.NotificationPriority.NORMAL,
                    java.util.Map.of("fromDate", request.getFromDate().toString(), "toDate", request.getToDate().toString(), "leaveType", request.getLeaveType().name())
            );
        } catch (Exception e) {
            log.error("Failed to send leave submission notification: {}", e.getMessage());
        }

        return saved;
    }

    private void validateLeaveRequest(LeaveRequest request, String excludeRequestId) {
        if (request.getLeaveType() == null) {
            throw new IllegalArgumentException("Leave type is required for leave request");
        }
        if (request.getLeaveType() == LeaveRequest.LeaveType.OTHER_LEAVE) {
            throw new IllegalArgumentException("Other Leave has been replaced by Loss of Pay (LOP). Please select Casual Leave, Medical Leave, Emergency Leave, Comp-Off, or Loss of Pay (LOP).");
        }
        if (request.getLeaveType() == LeaveRequest.LeaveType.UNPAID_LOP) {
            request.setLeaveType(LeaveRequest.LeaveType.LOP);
        }

        // LOP does not consume leave balance and is always permitted
        if (request.getLeaveType() == LeaveRequest.LeaveType.LOP) {
            return;
        }

        // Validate paid leave balance
        if (request.getLeaveType() == LeaveRequest.LeaveType.CASUAL_LEAVE
                || request.getLeaveType() == LeaveRequest.LeaveType.MEDICAL_LEAVE
                || request.getLeaveType() == LeaveRequest.LeaveType.EMERGENCY_LEAVE
                || request.getLeaveType() == LeaveRequest.LeaveType.COMP_OFF) {

            int year = request.getFromDate() != null ? request.getFromDate().getYear() : LocalDate.now().getYear();
            LeaveEntitlement entitlement = getOrCreateEntitlement(request.getEmployeeId(), year);

            // Compute pending days for this specific employee and leave type in this year, excluding excludeRequestId if present
            List<LeaveRequest> pendingRequests = leaveRequestRepository.findByEmployeeIdAndStatus(request.getEmployeeId(), LeaveRequest.LeaveStatus.PENDING);
            double pendingDaysForType = 0.0;
            if (pendingRequests != null) {
                for (LeaveRequest p : pendingRequests) {
                    if (excludeRequestId != null && excludeRequestId.equals(p.getId())) {
                        continue;
                    }
                    if (p.getFromDate() != null && p.getFromDate().getYear() == year && p.getLeaveType() == request.getLeaveType()) {
                        pendingDaysForType += (p.getNumberOfDays() != null ? p.getNumberOfDays() : 0.0);
                    }
                }
            }

            double remainingEntitlement = 0.0;
            String typeName = request.getLeaveType().name().replace('_', ' ');

            if (request.getLeaveType() == LeaveRequest.LeaveType.CASUAL_LEAVE) {
                remainingEntitlement = entitlement.getCasualLeaveRemaining();
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
                remainingEntitlement = entitlement.getMedicalLeaveRemaining();
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
                remainingEntitlement = entitlement.getEmergencyLeaveRemaining();
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.COMP_OFF) {
                remainingEntitlement = entitlement.getCompOffRemaining();
            }

            double availableBalance = Math.max(0.0, remainingEntitlement - pendingDaysForType);
            double requestedDays = request.getNumberOfDays() != null ? request.getNumberOfDays() : 0.0;

            if (availableBalance <= 0.0) {
                throw new IllegalArgumentException("Available balance for " + typeName + " is 0. All available paid leaves for this type are exhausted. You can only apply under Loss of Pay (LOP).");
            }

            if (requestedDays > availableBalance) {
                throw new IllegalArgumentException("Insufficient " + typeName + " balance. Requested: " + requestedDays + " day(s), but only " + availableBalance + " day(s) available. Please apply for up to " + availableBalance + " day(s) as " + typeName + " and submit the remaining days as LOP.");
            }
        }
    }

    @Transactional
    public LeaveRequest approveOrRejectLeave(String requestId, boolean approve, String adminRemarks, String adminUsername) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + requestId));

        if (request.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new IllegalStateException("Only PENDING leave requests can be approved/rejected");
        }

        if (approve) {
            request.setStatus(LeaveRequest.LeaveStatus.APPROVED);
            request.setApprovedBy(adminUsername);
            request.setApprovedDate(LocalDateTime.now());
            request.setAdminRemarks(adminRemarks);

            // Re-verify authoritative leave days
            double days = calculateLeaveDays(request.getFromDate(), request.getToDate(), request.getIsHalfDay());
            request.setNumberOfDays(days);

            boolean isLop = (request.getLeaveType() == LeaveRequest.LeaveType.LOP || request.getLeaveType() == LeaveRequest.LeaveType.UNPAID_LOP);

            // Update entitlement balance for paid leave only
            if (!isLop) {
                int year = request.getFromDate().getYear();
                LeaveEntitlement entitlement = getOrCreateEntitlement(request.getEmployeeId(), year);
                int numDays = (int) Math.ceil(days);

                if (request.getLeaveType() == LeaveRequest.LeaveType.CASUAL_LEAVE) {
                    entitlement.setCasualLeaveUsed(entitlement.getCasualLeaveUsed() + numDays);
                } else if (request.getLeaveType() == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
                    entitlement.setMedicalLeaveUsed(entitlement.getMedicalLeaveUsed() + numDays);
                } else if (request.getLeaveType() == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
                    entitlement.setEmergencyLeaveUsed(entitlement.getEmergencyLeaveUsed() + numDays);
                } else if (request.getLeaveType() == LeaveRequest.LeaveType.COMP_OFF) {
                    entitlement.setCompOffUsed((entitlement.getCompOffUsed() != null ? entitlement.getCompOffUsed() : 0) + numDays);
                }
                entitlementRepository.save(entitlement);
            }

            // Update Attendance records ONLY for eligible working days in leave date range
            CompanySettingsDto settings = companySettingsService != null ? companySettingsService.getSettings() : null;
            double expectedHours = (settings != null && settings.getWorkingHoursPerDay() != null && settings.getWorkingHoursPerDay() > 0)
                    ? settings.getWorkingHoursPerDay() : 8.0;

            List<LocalDate> eligibleDates = getEligibleLeaveDates(request.getFromDate(), request.getToDate());
            for (LocalDate loopDate : eligibleDates) {
                Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(request.getEmployeeId(), loopDate);
                AttendanceRecord record = recOpt.orElseGet(() -> AttendanceRecord.builder()
                        .employeeId(request.getEmployeeId())
                        .employeeCode(request.getEmployeeCode())
                        .employeeName(request.getEmployeeName())
                        .department(request.getDepartment())
                        .date(loopDate)
                        .totalWorkingHours(0.0)
                        .expectedWorkingHours(expectedHours)
                        .build());

                if (Boolean.TRUE.equals(request.getIsHalfDay())) {
                    record.setStatus(AttendanceRecord.AttendanceStatus.HALF_DAY);
                } else {
                    record.setStatus(AttendanceRecord.AttendanceStatus.LEAVE);
                }
                record.setLeaveType(request.getLeaveType());
                record.setIsLop(isLop);
                record.setRemarks("On approved leave (" + (isLop ? "LOP" : request.getLeaveType()) + ")");
                attendanceRecordRepository.save(record);
            }

            auditService.log("APPROVE_LEAVE", "LEAVE_MGMT", request.getId(), "Approved leave request for " + request.getEmployeeName());
        } else {
            request.setStatus(LeaveRequest.LeaveStatus.REJECTED);
            request.setApprovedBy(adminUsername);
            request.setApprovedDate(LocalDateTime.now());
            request.setAdminRemarks(adminRemarks);
            auditService.log("REJECT_LEAVE", "LEAVE_MGMT", request.getId(), "Rejected leave request for " + request.getEmployeeName());
        }

        LeaveRequest saved = leaveRequestRepository.save(request);

        // Send notification to applicant employee
        if (notificationService != null) {
            try {
                com.app.billing.model.Notification.NotificationType nType = approve ? 
                        com.app.billing.model.Notification.NotificationType.LEAVE_REQUEST_APPROVED : 
                        com.app.billing.model.Notification.NotificationType.LEAVE_REQUEST_REJECTED;
                String nTitle = approve ? "Leave Request Approved" : "Leave Request Rejected";
                String nMsg = approve ? 
                        "Your " + request.getLeaveType() + " request for " + request.getFromDate() + " to " + request.getToDate() + " has been approved." :
                        "Your " + request.getLeaveType() + " request for " + request.getFromDate() + " to " + request.getToDate() + " has been rejected. Reason: " + (adminRemarks != null && !adminRemarks.isBlank() ? adminRemarks : "N/A");

                notificationService.sendNotification(
                        request.getEmployeeId(),
                        nType,
                        nTitle,
                        nMsg,
                        com.app.billing.security.Modules.LEAVE_MGMT,
                        "LeaveRequest",
                        saved.getId(),
                        "/attendance/leaves",
                        com.app.billing.model.Notification.NotificationPriority.NORMAL,
                        java.util.Map.of("status", saved.getStatus().name(), "adminRemarks", adminRemarks != null ? adminRemarks : "")
                );
            } catch (Exception e) {
                log.error("Failed to send leave decision notification: {}", e.getMessage());
            }
        }

        return saved;
    }

    public List<LeaveRequest> getLeaveHistoryForEmployee(String employeeId) {
        List<LeaveRequest> list = leaveRequestRepository.findByEmployeeIdOrderByAppliedDateDesc(employeeId);
        enrichLeaveRequests(list);
        return list;
    }

    public List<LeaveRequest> getPendingLeaveRequests() {
        List<LeaveRequest> list = leaveRequestRepository.findByStatus(LeaveRequest.LeaveStatus.PENDING);
        enrichLeaveRequests(list);
        return list;
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        List<LeaveRequest> list = leaveRequestRepository.findAll();
        enrichLeaveRequests(list);
        return list;
    }

    private void enrichLeaveRequests(List<LeaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (LeaveRequest req : requests) {
            enrichLeaveRequest(req);
        }
    }

    private void enrichLeaveRequest(LeaveRequest req) {
        if (req == null) return;
        boolean missingName = req.getEmployeeName() == null || req.getEmployeeName().isBlank();
        boolean missingCode = req.getEmployeeCode() == null || req.getEmployeeCode().isBlank();
        if ((missingName || missingCode) && req.getEmployeeId() != null && !req.getEmployeeId().isBlank()) {
            employeeRepository.findById(req.getEmployeeId())
                    .or(() -> employeeRepository.findByEmployeeCode(req.getEmployeeId()))
                    .or(() -> employeeRepository.findByLoginId(req.getEmployeeId()))
                    .ifPresent(emp -> {
                        if (missingName && emp.getEmployeeName() != null) {
                            req.setEmployeeName(emp.getEmployeeName());
                        }
                        if (missingCode && emp.getEmployeeCode() != null) {
                            req.setEmployeeCode(emp.getEmployeeCode());
                        }
                    });
        }
        if (req.getEmployeeName() == null && req.getEmployeeCode() != null && !req.getEmployeeCode().isBlank()) {
            employeeRepository.findByEmployeeCode(req.getEmployeeCode())
                    .ifPresent(emp -> req.setEmployeeName(emp.getEmployeeName()));
        }
    }

    @Transactional
    public LeaveRequest updateLeave(String id, LeaveRequest updatedRequest) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + id));

        LeaveRequest.LeaveType oldType = existing.getLeaveType();
        LocalDate oldFrom = existing.getFromDate();
        LocalDate oldTo = existing.getToDate();
        double oldDays = existing.getNumberOfDays();
        boolean wasApproved = existing.getStatus() == LeaveRequest.LeaveStatus.APPROVED;

        if (updatedRequest.getLeaveType() != null) {
            existing.setLeaveType(updatedRequest.getLeaveType());
        }
        if (updatedRequest.getFromDate() != null) {
            existing.setFromDate(updatedRequest.getFromDate());
        }
        if (updatedRequest.getToDate() != null) {
            existing.setToDate(updatedRequest.getToDate());
        }
        if (updatedRequest.getIsHalfDay() != null) {
            existing.setIsHalfDay(updatedRequest.getIsHalfDay());
        }
        if (updatedRequest.getReason() != null) {
            existing.setReason(updatedRequest.getReason());
        }

        if (existing.getFromDate() != null && existing.getToDate() != null) {
            if (existing.getToDate().isBefore(existing.getFromDate())) {
                throw new IllegalArgumentException("To date cannot be before From date");
            }
            double days = calculateLeaveDays(existing.getFromDate(), existing.getToDate(), existing.getIsHalfDay());
            existing.setNumberOfDays(days);
        }

        if (!wasApproved) {
            validateLeaveRequest(existing, id);
        }

        // If the leave was already approved, safely adjust entitlement deduction and attendance records
        if (wasApproved && oldFrom != null && oldTo != null && existing.getFromDate() != null && existing.getToDate() != null) {
            boolean oldIsLop = (oldType == LeaveRequest.LeaveType.LOP || oldType == LeaveRequest.LeaveType.UNPAID_LOP);
            boolean newIsLop = (existing.getLeaveType() == LeaveRequest.LeaveType.LOP || existing.getLeaveType() == LeaveRequest.LeaveType.UNPAID_LOP);

            // Revert old entitlement deduction if old was paid
            if (!oldIsLop) {
                int oldYear = oldFrom.getYear();
                int oldNumDays = (int) Math.ceil(oldDays);
                Optional<LeaveEntitlement> entitlementOpt = entitlementRepository.findByEmployeeIdAndYear(existing.getEmployeeId(), oldYear);
                if (entitlementOpt.isPresent()) {
                    LeaveEntitlement entitlement = entitlementOpt.get();
                    revertEntitlement(entitlement, oldType, oldNumDays);
                    entitlementRepository.save(entitlement);
                }
            }

            // Apply new entitlement deduction if new is paid
            if (!newIsLop) {
                int newYear = existing.getFromDate().getYear();
                LeaveEntitlement newYearEnt = getOrCreateEntitlement(existing.getEmployeeId(), newYear);
                applyEntitlement(newYearEnt, existing.getLeaveType(), (int) Math.ceil(existing.getNumberOfDays()));
                entitlementRepository.save(newYearEnt);
            }

            // Clean up old attendance records on old eligible dates
            List<LocalDate> oldEligibleDates = getEligibleLeaveDates(oldFrom, oldTo);
            for (LocalDate loopDate : oldEligibleDates) {
                Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(existing.getEmployeeId(), loopDate);
                if (recOpt.isPresent()) {
                    AttendanceRecord record = recOpt.get();
                    if (record.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE || record.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY) {
                        if (record.getCheckInTime() != null) {
                            record.setStatus(AttendanceRecord.AttendanceStatus.PRESENT);
                            record.setRemarks(null);
                            record.setLeaveType(null);
                            record.setIsLop(null);
                            attendanceRecordRepository.save(record);
                        } else {
                            attendanceRecordRepository.delete(record);
                        }
                    }
                }
            }

            // Create new attendance records on new eligible dates
            CompanySettingsDto settings = companySettingsService != null ? companySettingsService.getSettings() : null;
            double expectedHours = (settings != null && settings.getWorkingHoursPerDay() != null && settings.getWorkingHoursPerDay() > 0)
                    ? settings.getWorkingHoursPerDay() : 8.0;

            List<LocalDate> newEligibleDates = getEligibleLeaveDates(existing.getFromDate(), existing.getToDate());
            for (LocalDate loopDate : newEligibleDates) {
                Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(existing.getEmployeeId(), loopDate);
                AttendanceRecord record = recOpt.orElseGet(() -> AttendanceRecord.builder()
                        .employeeId(existing.getEmployeeId())
                        .employeeCode(existing.getEmployeeCode())
                        .employeeName(existing.getEmployeeName())
                        .department(existing.getDepartment())
                        .date(loopDate)
                        .totalWorkingHours(0.0)
                        .expectedWorkingHours(expectedHours)
                        .build());

                if (Boolean.TRUE.equals(existing.getIsHalfDay())) {
                    record.setStatus(AttendanceRecord.AttendanceStatus.HALF_DAY);
                } else {
                    record.setStatus(AttendanceRecord.AttendanceStatus.LEAVE);
                }
                record.setLeaveType(existing.getLeaveType());
                record.setIsLop(newIsLop);
                record.setRemarks("On approved leave (" + (newIsLop ? "LOP" : existing.getLeaveType()) + ")");
                attendanceRecordRepository.save(record);
            }
        }

        LeaveRequest saved = leaveRequestRepository.save(existing);
        enrichLeaveRequest(saved);
        auditService.log("UPDATE_LEAVE", "LEAVE_MGMT", saved.getId(), "Updated leave request ID: " + id);
        return saved;
    }

    private void revertEntitlement(LeaveEntitlement entitlement, LeaveRequest.LeaveType type, int days) {
        if (type == LeaveRequest.LeaveType.LOP || type == LeaveRequest.LeaveType.UNPAID_LOP) {
            return;
        }
        if (type == LeaveRequest.LeaveType.CASUAL_LEAVE) {
            entitlement.setCasualLeaveUsed(Math.max(0, entitlement.getCasualLeaveUsed() - days));
        } else if (type == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
            entitlement.setMedicalLeaveUsed(Math.max(0, entitlement.getMedicalLeaveUsed() - days));
        } else if (type == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
            entitlement.setEmergencyLeaveUsed(Math.max(0, entitlement.getEmergencyLeaveUsed() - days));
        } else if (type == LeaveRequest.LeaveType.COMP_OFF) {
            int currentCompOff = entitlement.getCompOffUsed() != null ? entitlement.getCompOffUsed() : 0;
            entitlement.setCompOffUsed(Math.max(0, currentCompOff - days));
        } else if (type == LeaveRequest.LeaveType.OTHER_LEAVE) {
            entitlement.setOtherLeaveUsed(Math.max(0, entitlement.getOtherLeaveUsed() - days));
        }
    }

    private void applyEntitlement(LeaveEntitlement entitlement, LeaveRequest.LeaveType type, int days) {
        if (type == LeaveRequest.LeaveType.LOP || type == LeaveRequest.LeaveType.UNPAID_LOP) {
            return;
        }
        if (type == LeaveRequest.LeaveType.CASUAL_LEAVE) {
            entitlement.setCasualLeaveUsed(entitlement.getCasualLeaveUsed() + days);
        } else if (type == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
            entitlement.setMedicalLeaveUsed(entitlement.getMedicalLeaveUsed() + days);
        } else if (type == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
            entitlement.setEmergencyLeaveUsed(entitlement.getEmergencyLeaveUsed() + days);
        } else if (type == LeaveRequest.LeaveType.COMP_OFF) {
            int currentCompOff = entitlement.getCompOffUsed() != null ? entitlement.getCompOffUsed() : 0;
            entitlement.setCompOffUsed(currentCompOff + days);
        } else if (type == LeaveRequest.LeaveType.OTHER_LEAVE) {
            entitlement.setOtherLeaveUsed(entitlement.getOtherLeaveUsed() + days);
        }
    }

    @Transactional
    public void deleteLeave(String id) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + id));

        // If the leave was approved, revert entitlements and attendance records
        if (existing.getStatus() == LeaveRequest.LeaveStatus.APPROVED && existing.getFromDate() != null) {
            boolean isLop = (existing.getLeaveType() == LeaveRequest.LeaveType.LOP || existing.getLeaveType() == LeaveRequest.LeaveType.UNPAID_LOP);
            if (!isLop) {
                int year = existing.getFromDate().getYear();
                int numDays = (int) Math.ceil(existing.getNumberOfDays());

                Optional<LeaveEntitlement> entitlementOpt = entitlementRepository.findByEmployeeIdAndYear(existing.getEmployeeId(), year);
                if (entitlementOpt.isPresent()) {
                    LeaveEntitlement entitlement = entitlementOpt.get();
                    revertEntitlement(entitlement, existing.getLeaveType(), numDays);
                    entitlementRepository.save(entitlement);
                }
            }

            if (existing.getToDate() != null) {
                List<LocalDate> eligibleDates = getEligibleLeaveDates(existing.getFromDate(), existing.getToDate());
                for (LocalDate loopDate : eligibleDates) {
                    Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(existing.getEmployeeId(), loopDate);
                    if (recOpt.isPresent()) {
                        AttendanceRecord record = recOpt.get();
                        if (record.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE || record.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY) {
                            if (record.getCheckInTime() != null) {
                                record.setStatus(AttendanceRecord.AttendanceStatus.PRESENT);
                                record.setRemarks(null);
                                record.setLeaveType(null);
                                record.setIsLop(null);
                                attendanceRecordRepository.save(record);
                            } else {
                                attendanceRecordRepository.delete(record);
                            }
                        }
                    }
                }
            }
        }

        leaveRequestRepository.delete(existing);
        auditService.log("DELETE_LEAVE", "LEAVE_MGMT", id, "Deleted leave request for " + existing.getEmployeeName());
    }
}

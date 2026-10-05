package com.app.billing.service;

import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.LeaveEntitlementRepository;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.CompanySettings;
import com.app.billing.model.Employee;
import com.app.billing.model.LeaveEntitlement;
import com.app.billing.model.LeaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveManagementService {

    private final LeaveEntitlementRepository entitlementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanySettingsService companySettingsService;
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

        if (entitlementOpt.isPresent()) {
            LeaveEntitlement entitlement = entitlementOpt.get();
            entitlement.setCasualLeaveTotal(casual);
            entitlement.setMedicalLeaveTotal(medical);
            entitlement.setEmergencyLeaveTotal(emergency);
            entitlement.setCompOffTotal(compOff);
            LeaveEntitlement saved = entitlementRepository.save(entitlement);
            return saved != null ? saved : entitlement;
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

        LeaveEntitlement entitlement = LeaveEntitlement.builder()
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

        LeaveEntitlement saved = entitlementRepository.save(entitlement);
        return saved != null ? saved : entitlement;
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

    @Transactional
    public LeaveRequest applyLeave(LeaveRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + request.getEmployeeId()));

        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("From date and To date are required for leave request");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("To date cannot be before From date");
        }

        double days = ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;
        if (Boolean.TRUE.equals(request.getIsHalfDay())) {
            days = 0.5;
        }

        request.setEmployeeCode(employee.getEmployeeCode());
        request.setEmployeeName(employee.getEmployeeName());
        request.setDepartment(employee.getCategory());
        request.setNumberOfDays(days);
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

            // Update entitlement balance
            int year = request.getFromDate().getYear();
            LeaveEntitlement entitlement = getOrCreateEntitlement(request.getEmployeeId(), year);
            int numDays = (int) Math.ceil(request.getNumberOfDays());

            if (request.getLeaveType() == LeaveRequest.LeaveType.CASUAL_LEAVE) {
                entitlement.setCasualLeaveUsed(entitlement.getCasualLeaveUsed() + numDays);
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
                entitlement.setMedicalLeaveUsed(entitlement.getMedicalLeaveUsed() + numDays);
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
                entitlement.setEmergencyLeaveUsed(entitlement.getEmergencyLeaveUsed() + numDays);
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.COMP_OFF) {
                entitlement.setCompOffUsed((entitlement.getCompOffUsed() != null ? entitlement.getCompOffUsed() : 0) + numDays);
            } else if (request.getLeaveType() == LeaveRequest.LeaveType.OTHER_LEAVE) {
                entitlement.setOtherLeaveUsed(entitlement.getOtherLeaveUsed() + numDays);
            }
            entitlementRepository.save(entitlement);

            // Update Attendance records for the leave date range
            LocalDate current = request.getFromDate();
            while (!current.isAfter(request.getToDate())) {
                final LocalDate loopDate = current;
                Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(request.getEmployeeId(), loopDate);
                AttendanceRecord record = recOpt.orElseGet(() -> AttendanceRecord.builder()
                        .employeeId(request.getEmployeeId())
                        .employeeCode(request.getEmployeeCode())
                        .employeeName(request.getEmployeeName())
                        .department(request.getDepartment())
                        .date(loopDate)
                        .totalWorkingHours(0.0)
                        .expectedWorkingHours(8.0)
                        .build());

                if (Boolean.TRUE.equals(request.getIsHalfDay())) {
                    record.setStatus(AttendanceRecord.AttendanceStatus.HALF_DAY);
                } else {
                    record.setStatus(AttendanceRecord.AttendanceStatus.LEAVE);
                }
                record.setRemarks("On approved leave (" + request.getLeaveType() + ")");
                attendanceRecordRepository.save(record);

                current = current.plusDays(1);
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
        return leaveRequestRepository.findByEmployeeIdOrderByAppliedDateDesc(employeeId);
    }

    public List<LeaveRequest> getPendingLeaveRequests() {
        return leaveRequestRepository.findByStatus(LeaveRequest.LeaveStatus.PENDING);
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }

    @Transactional
    public LeaveRequest updateLeave(String id, LeaveRequest updatedRequest) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + id));

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
            double days = ChronoUnit.DAYS.between(existing.getFromDate(), existing.getToDate()) + 1;
            if (Boolean.TRUE.equals(existing.getIsHalfDay())) {
                days = 0.5;
            }
            existing.setNumberOfDays(days);
        }

        LeaveRequest saved = leaveRequestRepository.save(existing);
        auditService.log("UPDATE_LEAVE", "LEAVE_MGMT", saved.getId(), "Updated leave request ID: " + id);
        return saved;
    }

    @Transactional
    public void deleteLeave(String id) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with ID: " + id));

        // If the leave was approved, revert entitlements and attendance records
        if (existing.getStatus() == LeaveRequest.LeaveStatus.APPROVED && existing.getFromDate() != null) {
            int year = existing.getFromDate().getYear();
            int numDays = (int) Math.ceil(existing.getNumberOfDays());

            Optional<LeaveEntitlement> entitlementOpt = entitlementRepository.findByEmployeeIdAndYear(existing.getEmployeeId(), year);
            if (entitlementOpt.isPresent()) {
                LeaveEntitlement entitlement = entitlementOpt.get();
                if (existing.getLeaveType() == LeaveRequest.LeaveType.CASUAL_LEAVE) {
                    entitlement.setCasualLeaveUsed(Math.max(0, entitlement.getCasualLeaveUsed() - numDays));
                } else if (existing.getLeaveType() == LeaveRequest.LeaveType.MEDICAL_LEAVE) {
                    entitlement.setMedicalLeaveUsed(Math.max(0, entitlement.getMedicalLeaveUsed() - numDays));
                } else if (existing.getLeaveType() == LeaveRequest.LeaveType.EMERGENCY_LEAVE) {
                    entitlement.setEmergencyLeaveUsed(Math.max(0, entitlement.getEmergencyLeaveUsed() - numDays));
                } else if (existing.getLeaveType() == LeaveRequest.LeaveType.COMP_OFF) {
                    int currentCompOff = entitlement.getCompOffUsed() != null ? entitlement.getCompOffUsed() : 0;
                    entitlement.setCompOffUsed(Math.max(0, currentCompOff - numDays));
                } else if (existing.getLeaveType() == LeaveRequest.LeaveType.OTHER_LEAVE) {
                    entitlement.setOtherLeaveUsed(Math.max(0, entitlement.getOtherLeaveUsed() - numDays));
                }
                entitlementRepository.save(entitlement);
            }

            if (existing.getToDate() != null) {
                LocalDate current = existing.getFromDate();
                while (!current.isAfter(existing.getToDate())) {
                    final LocalDate loopDate = current;
                    Optional<AttendanceRecord> recOpt = attendanceRecordRepository.findByEmployeeIdAndDate(existing.getEmployeeId(), loopDate);
                    if (recOpt.isPresent()) {
                        AttendanceRecord record = recOpt.get();
                        if (record.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE || record.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY) {
                            if (record.getCheckInTime() != null) {
                                record.setStatus(AttendanceRecord.AttendanceStatus.PRESENT);
                                record.setRemarks(null);
                                attendanceRecordRepository.save(record);
                            } else {
                                attendanceRecordRepository.delete(record);
                            }
                        }
                    }
                    current = current.plusDays(1);
                }
            }
        }

        leaveRequestRepository.delete(existing);
        auditService.log("DELETE_LEAVE", "LEAVE_MGMT", id, "Deleted leave request for " + existing.getEmployeeName());
    }
}

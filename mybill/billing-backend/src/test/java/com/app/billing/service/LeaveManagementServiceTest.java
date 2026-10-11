package com.app.billing.service;

import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.LeaveEntitlementRepository;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.Employee;
import com.app.billing.model.LeaveEntitlement;
import com.app.billing.model.LeaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveManagementServiceTest {

    @Mock
    private LeaveEntitlementRepository entitlementRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private CompanySettingsService companySettingsService;
    @Mock
    private com.app.billing.dao.HolidayRepository holidayRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private LeaveManagementService leaveService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id("EMP123")
                .employeeCode("EMP001")
                .employeeName("Jane Smith")
                .category("Manager")
                .status(Employee.Status.ACTIVE)
                .build();
    }

    @Test
    void testApplyLeaveSuccess() {
        when(employeeRepository.findById("EMP123")).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequest request = LeaveRequest.builder()
                .employeeId("EMP123")
                .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                .fromDate(LocalDate.of(2026, 9, 21)) // Monday
                .toDate(LocalDate.of(2026, 9, 22))   // Tuesday
                .reason("Personal work")
                .build();

        LeaveRequest result = leaveService.applyLeave(request);

        assertNotNull(result);
        assertEquals(2.0, result.getNumberOfDays());
        assertEquals(LeaveRequest.LeaveStatus.PENDING, result.getStatus());
        assertEquals("EMP001", result.getEmployeeCode());
    }

    @Test
    void testApproveLeaveUpdatesEntitlement() {
        LeaveRequest request = LeaveRequest.builder()
                .id("REQ1")
                .employeeId("EMP123")
                .employeeCode("EMP001")
                .employeeName("Jane Smith")
                .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                .fromDate(LocalDate.of(2026, 9, 21)) // Monday
                .toDate(LocalDate.of(2026, 9, 22))   // Tuesday
                .numberOfDays(2.0)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        LeaveEntitlement entitlement = LeaveEntitlement.builder()
                .employeeId("EMP123")
                .year(2026)
                .casualLeaveTotal(12)
                .casualLeaveUsed(0)
                .build();

        when(leaveRequestRepository.findById("REQ1")).thenReturn(Optional.of(request));
        when(entitlementRepository.findByEmployeeIdAndYear("EMP123", 2026)).thenReturn(Optional.of(entitlement));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequest approved = leaveService.approveOrRejectLeave("REQ1", true, "Approved", "admin@billing.com");

        assertEquals(LeaveRequest.LeaveStatus.APPROVED, approved.getStatus());
        assertEquals(2, entitlement.getCasualLeaveUsed());
        assertEquals(10, entitlement.getCasualLeaveRemaining());
    }

    @Test
    void testGetAllLeaveRequestsEnrichesMissingEmployeeDetails() {
        LeaveRequest legacyRequest = LeaveRequest.builder()
                .id("LEGACY_REQ")
                .employeeId("EMP123")
                .employeeCode(null)
                .employeeName(null)
                .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                .fromDate(LocalDate.of(2026, 10, 1))
                .toDate(LocalDate.of(2026, 10, 2))
                .numberOfDays(2.0)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        when(leaveRequestRepository.findAll()).thenReturn(java.util.List.of(legacyRequest));
        when(employeeRepository.findById("EMP123")).thenReturn(Optional.of(employee));

        java.util.List<LeaveRequest> list = leaveService.getAllLeaveRequests();

        assertEquals(1, list.size());
        assertEquals("Jane Smith", list.get(0).getEmployeeName());
        assertEquals("EMP001", list.get(0).getEmployeeCode());
    }

    @Test
    void testApplyLeaveNullEmployeeIdThrowsMeaningfulException() {
        LeaveRequest request = LeaveRequest.builder()
                .employeeId(null)
                .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                .fromDate(LocalDate.of(2026, 9, 20))
                .toDate(LocalDate.of(2026, 9, 21))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveService.applyLeave(request));
        assertTrue(ex.getMessage().contains("Employee ID is required"));
    }

    @Test
    void testApplyLeaveResolvesEmployeeByCode() {
        when(employeeRepository.findById("EMP001")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmployeeCode("EMP001")).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveRequest request = LeaveRequest.builder()
                .employeeId("EMP001")
                .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                .fromDate(LocalDate.of(2026, 9, 21)) // Monday
                .toDate(LocalDate.of(2026, 9, 22))   // Tuesday
                .build();

        LeaveRequest result = leaveService.applyLeave(request);
        assertEquals("EMP123", result.getEmployeeId());
        assertEquals("EMP001", result.getEmployeeCode());
        assertEquals("Jane Smith", result.getEmployeeName());
    }

    @Test
    void testFridayToTuesdayExcludesSundayWeeklyOff() {
        // Friday (2026-10-09) to Tuesday (2026-10-13) = 5 calendar days: Fri, Sat, Sun, Mon, Tue
        CompanySettingsDto settings = CompanySettingsDto.builder()
                .weekendDays(java.util.List.of("SUNDAY"))
                .build();
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(java.util.List.of());

        double days = leaveService.calculateLeaveDays(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13), false);

        // Sunday is excluded, leaving 4 working days (Fri, Sat, Mon, Tue)
        assertEquals(4.0, days);
    }

    @Test
    void testLeaveWithOrganizationHolidayExclusion() {
        // Friday (2026-10-09) to Tuesday (2026-10-13), Sunday is off, Monday is organization holiday
        CompanySettingsDto settings = CompanySettingsDto.builder()
                .weekendDays(java.util.List.of("SUNDAY"))
                .build();
        when(companySettingsService.getSettings()).thenReturn(settings);

        com.app.billing.model.Holiday holiday = com.app.billing.model.Holiday.builder()
                .holidayDate(LocalDate.of(2026, 10, 12)) // Monday
                .holidayName("Company Day")
                .active(true)
                .build();
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(java.util.List.of(holiday));

        double days = leaveService.calculateLeaveDays(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13), false);

        // Sunday is off, Monday is holiday => 3 working days (Fri, Sat, Tue)
        assertEquals(3.0, days);
    }

    @Test
    void testDateThatIsBothWeeklyOffAndOrganizationHolidayExcludedOnlyOnce() {
        // Sunday (2026-10-11) is weekly off and also in Holiday collection
        CompanySettingsDto settings = CompanySettingsDto.builder()
                .weekendDays(java.util.List.of("SUNDAY"))
                .build();
        when(companySettingsService.getSettings()).thenReturn(settings);

        com.app.billing.model.Holiday holiday = com.app.billing.model.Holiday.builder()
                .holidayDate(LocalDate.of(2026, 10, 11)) // Sunday
                .holidayName("Diwali on Sunday")
                .active(true)
                .build();
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(java.util.List.of(holiday));

        double days = leaveService.calculateLeaveDays(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 13), false);

        // Sunday excluded once (never double deducted) => 4 working days
        assertEquals(4.0, days);
    }

    @Test
    void testMultipleWeeklyOffDaysAcrossMultiWeekRange() {
        // 2-week range: Saturday & Sunday weekly off (Fri 2026-10-02 to Mon 2026-10-12 = 11 calendar days)
        // Weekend days: Sat Oct 3, Sun Oct 4, Sat Oct 10, Sun Oct 11 = 4 weekend days
        // Working days: 11 - 4 = 7 days
        CompanySettingsDto settings = CompanySettingsDto.builder()
                .weekendDays(java.util.List.of("SATURDAY", "SUNDAY"))
                .build();
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(java.util.List.of());

        double days = leaveService.calculateLeaveDays(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 12), false);

        assertEquals(7.0, days);
    }

    @Test
    void testDateRangeContainingOnlyExcludedDays() {
        // Range: Saturday to Sunday, both configured as weekly off
        CompanySettingsDto settings = CompanySettingsDto.builder()
                .weekendDays(java.util.List.of("SATURDAY", "SUNDAY"))
                .build();
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(java.util.List.of());

        double days = leaveService.calculateLeaveDays(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), false);

        assertEquals(0.0, days);
    }

    @Test
    void testLeaveEditOnApprovedLeaveAdjustsBalanceWithoutDuplicateDeduction() {
        CompanySettingsDto settings = CompanySettingsDto.builder()
                .weekendDays(java.util.List.of("SUNDAY"))
                .workingHoursPerDay(8.0)
                .build();
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(java.util.List.of());

        LeaveRequest approvedRequest = LeaveRequest.builder()
                .id("REQ100")
                .employeeId("EMP123")
                .employeeCode("EMP001")
                .employeeName("Jane Smith")
                .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                .fromDate(LocalDate.of(2026, 10, 9))
                .toDate(LocalDate.of(2026, 10, 13))
                .numberOfDays(4.0) // 4 working days initially approved
                .status(LeaveRequest.LeaveStatus.APPROVED)
                .build();

        LeaveEntitlement entitlement = LeaveEntitlement.builder()
                .employeeId("EMP123")
                .year(2026)
                .casualLeaveTotal(12)
                .casualLeaveUsed(4) // 4 days currently used
                .build();

        when(leaveRequestRepository.findById("REQ100")).thenReturn(Optional.of(approvedRequest));
        when(entitlementRepository.findByEmployeeIdAndYear("EMP123", 2026)).thenReturn(Optional.of(entitlement));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Employee edits leave dates to Friday (Oct 9) to Saturday (Oct 10) = 2 working days
        LeaveRequest updateDto = LeaveRequest.builder()
                .fromDate(LocalDate.of(2026, 10, 9))
                .toDate(LocalDate.of(2026, 10, 10))
                .build();

        LeaveRequest updated = leaveService.updateLeave("REQ100", updateDto);

        assertEquals(2.0, updated.getNumberOfDays());
        // Entitlement used must now reflect 2 days (4 - 4 + 2 = 2), NOT 6 days
        assertEquals(2, entitlement.getCasualLeaveUsed());
        assertEquals(10, entitlement.getCasualLeaveRemaining());
    }
}

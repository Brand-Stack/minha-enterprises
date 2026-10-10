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
                .fromDate(LocalDate.of(2026, 9, 20))
                .toDate(LocalDate.of(2026, 9, 21))
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
                .fromDate(LocalDate.of(2026, 9, 20))
                .toDate(LocalDate.of(2026, 9, 21))
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
                .fromDate(LocalDate.of(2026, 9, 20))
                .toDate(LocalDate.of(2026, 9, 21))
                .build();

        LeaveRequest result = leaveService.applyLeave(request);
        assertEquals("EMP123", result.getEmployeeId());
        assertEquals("EMP001", result.getEmployeeCode());
        assertEquals("Jane Smith", result.getEmployeeName());
    }
}

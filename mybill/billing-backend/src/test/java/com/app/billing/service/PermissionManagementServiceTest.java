package com.app.billing.service;

import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.PermissionRequestRepository;
import com.app.billing.model.Employee;
import com.app.billing.model.PermissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionManagementServiceTest {

    @Mock
    private PermissionRequestRepository permissionRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private CompanySettingsService companySettingsService;
    @Mock
    private AttendanceCalculationEngine calculationEngine;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PermissionManagementService permissionService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id("EMP456")
                .employeeCode("EMP002")
                .employeeName("John Doe")
                .build();
    }

    @Test
    void testGetAllPermissionRequestsEnrichesMissingEmployeeDetails() {
        PermissionRequest legacyRequest = PermissionRequest.builder()
                .id("PERM1")
                .employeeId("EMP456")
                .employeeCode(null)
                .employeeName(null)
                .date(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .durationMinutes(60)
                .status(PermissionRequest.PermissionStatus.PENDING)
                .build();

        when(permissionRepository.findAll()).thenReturn(List.of(legacyRequest));
        when(employeeRepository.findById("EMP456")).thenReturn(Optional.of(employee));

        List<PermissionRequest> list = permissionService.getAllPermissionRequests();

        assertEquals(1, list.size());
        assertEquals("John Doe", list.get(0).getEmployeeName());
        assertEquals("EMP002", list.get(0).getEmployeeCode());
    }

    @Test
    void testRequestPermissionNullEmployeeIdThrowsMeaningfulException() {
        PermissionRequest request = PermissionRequest.builder()
                .employeeId(null)
                .date(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> permissionService.requestPermission(request));
        assertTrue(ex.getMessage().contains("Employee ID is required"));
    }

    @Test
    void testRequestPermissionResolvesByEmployeeCode() {
        when(employeeRepository.findById("EMP002")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmployeeCode("EMP002")).thenReturn(Optional.of(employee));
        when(permissionRepository.save(any(PermissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        PermissionRequest request = PermissionRequest.builder()
                .employeeId("EMP002")
                .date(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        PermissionRequest result = permissionService.requestPermission(request);
        assertEquals("EMP456", result.getEmployeeId());
        assertEquals("EMP002", result.getEmployeeCode());
        assertEquals("John Doe", result.getEmployeeName());
        assertEquals(60, result.getDurationMinutes());
    }
}

package com.app.billing.service;

import com.app.billing.dao.AdvanceTransactionRepository;
import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeAdvanceAccountRepository;
import com.app.billing.dao.EmployeeBonusRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.OvertimeRecordRepository;
import com.app.billing.dao.PayrollRecordRepository;
import com.app.billing.dao.SalaryStructureRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.AdvanceTransaction;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.Employee;
import com.app.billing.model.EmployeeAdvanceAccount;
import com.app.billing.model.PayrollRecord;
import com.app.billing.model.SalaryStructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollCalculationServiceTest {

    @Mock
    private SalaryStructureRepository salaryStructureRepository;
    @Mock
    private EmployeeAdvanceAccountRepository employeeAdvanceAccountRepository;
    @Mock
    private AdvanceTransactionRepository advanceTransactionRepository;
    @Mock
    private EmployeeBonusRepository bonusRepository;
    @Mock
    private PayrollRecordRepository payrollRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private OvertimeRecordRepository overtimeRecordRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private CompanySettingsService companySettingsService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PayrollCalculationService payrollService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id("EMP100")
                .employeeCode("EMP100")
                .employeeName("Alice Cooper")
                .category("Manager")
                .bankName("HDFC Bank")
                .accountNumber("1234567890")
                .ifscCode("HDFC0001234")
                .status(Employee.Status.ACTIVE)
                .build();
    }

    @Test
    void testProcessPayrollCalculationWithLop() {
        SalaryStructure structure = SalaryStructure.builder()
                .employeeId("EMP100")
                .basicSalary(BigDecimal.valueOf(30000))
                .hra(BigDecimal.valueOf(10000))
                .allowances(BigDecimal.valueOf(5000))
                .pfDeduction(BigDecimal.valueOf(1800))
                .professionalTax(BigDecimal.valueOf(200))
                .incomeTax(BigDecimal.ZERO)
                .build();

        CompanySettingsDto settings = CompanySettingsDto.builder()
                .workingHoursPerDay(8.0)
                .overtimeHourlyRateMultiplier(1.5)
                .weekendDays(List.of("SUNDAY"))
                .build();

        List<AttendanceRecord> records = new ArrayList<>();
        for (int d = 1; d <= 28; d++) {
            records.add(AttendanceRecord.builder()
                    .employeeId("EMP100")
                    .date(LocalDate.of(2026, 9, d))
                    .status(AttendanceRecord.AttendanceStatus.PRESENT)
                    .build());
        }
        records.add(AttendanceRecord.builder()
                .employeeId("EMP100")
                .date(LocalDate.of(2026, 9, 29))
                .status(AttendanceRecord.AttendanceStatus.ABSENT)
                .build());
        records.add(AttendanceRecord.builder()
                .employeeId("EMP100")
                .date(LocalDate.of(2026, 9, 30))
                .status(AttendanceRecord.AttendanceStatus.ABSENT)
                .build());

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(structure));
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(records);
        when(bonusRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(List.of());
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(advanceTransactionRepository.findByEmployeeIdOrderByTransactionDateDescCreatedAtDesc("EMP100")).thenReturn(List.of());

        PayrollRecord result = payrollService.processPayrollForEmployee("EMP100", "2026-09", "admin@billing.com");

        assertNotNull(result);
        assertEquals(2.0, result.getLopDays());
        assertEquals(new BigDecimal("2307.70"), result.getLopAmount());
        assertEquals(new BigDecimal("45000.00"), result.getGrossSalary());
        assertEquals(new BigDecimal("4307.70"), result.getTotalDeductions());
        assertEquals(new BigDecimal("40692.30"), result.getNetSalary());
    }

    @Test
    void testEndToEndAdvanceAndPayrollDeductionScenario() {
        SalaryStructure structure = SalaryStructure.builder()
                .employeeId("EMP100")
                .basicSalary(BigDecimal.valueOf(30000))
                .hra(BigDecimal.ZERO)
                .allowances(BigDecimal.ZERO)
                .pfDeduction(BigDecimal.ZERO)
                .professionalTax(BigDecimal.ZERO)
                .incomeTax(BigDecimal.ZERO)
                .build();

        CompanySettingsDto settings = CompanySettingsDto.builder()
                .workingHoursPerDay(8.0)
                .overtimeHourlyRateMultiplier(1.5)
                .weekendDays(List.of("SUNDAY"))
                .build();

        List<AttendanceRecord> records = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            records.add(AttendanceRecord.builder()
                    .employeeId("EMP100")
                    .date(LocalDate.of(2026, 9, d))
                    .status(AttendanceRecord.AttendanceStatus.PRESENT)
                    .build());
        }

        EmployeeAdvanceAccount account = EmployeeAdvanceAccount.builder()
                .id("ACC100")
                .employeeId("EMP100")
                .employeeCode("EMP100")
                .employeeName("Alice Cooper")
                .totalAdvanceGiven(BigDecimal.valueOf(70000))
                .totalRepaid(BigDecimal.valueOf(10000))
                .totalPayrollDeducted(BigDecimal.ZERO)
                .outstandingBalance(BigDecimal.valueOf(60000))
                .status("ACTIVE")
                .build();

        AdvanceTransaction tx1 = AdvanceTransaction.builder()
                .employeeId("EMP100")
                .transactionType(AdvanceTransaction.TransactionType.ADVANCE_GIVEN)
                .amount(BigDecimal.valueOf(50000))
                .transactionDate(LocalDate.of(2026, 9, 1))
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .status("COMPLETED")
                .build();
        AdvanceTransaction tx2 = AdvanceTransaction.builder()
                .employeeId("EMP100")
                .transactionType(AdvanceTransaction.TransactionType.ADVANCE_GIVEN)
                .amount(BigDecimal.valueOf(20000))
                .transactionDate(LocalDate.of(2026, 9, 15))
                .createdAt(LocalDateTime.of(2026, 9, 15, 10, 0))
                .status("COMPLETED")
                .build();
        AdvanceTransaction tx3 = AdvanceTransaction.builder()
                .employeeId("EMP100")
                .transactionType(AdvanceTransaction.TransactionType.REPAYMENT)
                .amount(BigDecimal.valueOf(10000))
                .transactionDate(LocalDate.of(2026, 9, 20))
                .createdAt(LocalDateTime.of(2026, 9, 20, 10, 0))
                .status("COMPLETED")
                .build();

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(structure));
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(records);
        when(bonusRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(List.of());
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(account));
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(advanceTransactionRepository.findByEmployeeIdOrderByTransactionDateDescCreatedAtDesc("EMP100")).thenReturn(List.of(tx1, tx2, tx3));

        PayrollRecord result = payrollService.processPayrollForEmployee("EMP100", "2026-09", BigDecimal.valueOf(15000), "admin@billing.com");

        assertNotNull(result);
        assertEquals(0, new BigDecimal("30000").compareTo(result.getGrossSalary()));
        assertEquals(0, new BigDecimal("15000").compareTo(result.getAdvanceDeductionAmount()));
        assertEquals(0, new BigDecimal("15000").compareTo(result.getNetSalary()));
    }

    @Test
    void testPayrollCarryForwardToFutureMonth() {
        Employee employee = Employee.builder()
                .id("EMP100")
                .employeeCode("EMP100")
                .employeeName("John Doe")
                .status(Employee.Status.ACTIVE)
                .build();

        PayrollRecord septRecord = PayrollRecord.builder()
                .employeeId("EMP100")
                .payrollMonth("2026-09")
                .basicSalary(new BigDecimal("50000.00"))
                .hra(new BigDecimal("15000.00"))
                .allowances(new BigDecimal("8000.00"))
                .pfAmount(new BigDecimal("1800.00"))
                .professionalTax(new BigDecimal("200.00"))
                .build();

        CompanySettingsDto settings = CompanySettingsDto.builder()
                .workingHoursPerDay(8.0)
                .overtimeHourlyRateMultiplier(1.5)
                .weekendDays(List.of("SUNDAY"))
                .build();

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(List.of());
        when(bonusRepository.findByEmployeeIdAndPayrollMonth(eq("EMP100"), any())).thenReturn(List.of());
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-10")).thenReturn(Optional.empty());
        when(payrollRepository.findFirstByEmployeeIdAndPayrollMonthLessThanOrderByPayrollMonthDesc(eq("EMP100"), any())).thenReturn(Optional.of(septRecord));
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRecord octResult = payrollService.processPayrollForEmployee("EMP100", "2026-10", "admin@billing.com");

        assertNotNull(octResult);
        assertEquals(new BigDecimal("50000.00"), octResult.getBasicSalary());
        assertEquals(new BigDecimal("15000.00"), octResult.getHra());
        assertEquals(new BigDecimal("8000.00"), octResult.getAllowances());
        assertEquals(new BigDecimal("73000.00"), octResult.getGrossSalary());
    }
}

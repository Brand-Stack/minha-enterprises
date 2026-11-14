package com.app.billing.service;

import com.app.billing.dao.AdvanceTransactionRepository;
import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.EmployeeAdvanceAccountRepository;
import com.app.billing.dao.EmployeeBonusRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dao.HolidayRepository;
import com.app.billing.dao.LeaveRequestRepository;
import com.app.billing.dao.OvertimeRecordRepository;
import com.app.billing.dao.PayrollRecordRepository;
import com.app.billing.dao.SalaryStructureRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.AdvanceTransaction;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.Employee;
import com.app.billing.model.EmployeeAdvanceAccount;
import com.app.billing.model.Holiday;
import com.app.billing.model.LeaveRequest;
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
    private HolidayRepository holidayRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
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

    @Test
    void testManualSalaryOverridePreservedOnRegeneration() {
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

        // 1. Initial draft saved with manual overrides on basicSalary (40,000) and hra (12,000)
        PayrollRecord draft = PayrollRecord.builder()
                .employeeId("EMP100")
                .payrollMonth("2026-09")
                .basicSalary(BigDecimal.valueOf(40000))
                .hra(BigDecimal.valueOf(12000))
                .allowances(BigDecimal.valueOf(5000))
                .pfAmount(BigDecimal.valueOf(1800))
                .professionalTax(BigDecimal.valueOf(200))
                .taxAmount(BigDecimal.ZERO)
                .build();
        draft.setFieldOverridden("basicSalary", true);
        draft.setFieldOverridden("hra", true);

        List<AttendanceRecord> records = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            records.add(AttendanceRecord.builder()
                    .employeeId("EMP100")
                    .date(LocalDate.of(2026, 9, d))
                    .status(AttendanceRecord.AttendanceStatus.PRESENT)
                    .build());
        }

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(structure));
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(records);
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRecord savedDraft = payrollService.saveDraftPayroll(draft, "admin@billing.com");
        assertNotNull(savedDraft);
        assertTrue(savedDraft.isFieldOverridden("basicSalary"));
        assertTrue(savedDraft.isFieldOverridden("hra"));

        // 2. Now regenerate/reprocess payroll for the same employee and month
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.of(savedDraft));
        PayrollRecord regenerated = payrollService.processPayrollForEmployee("EMP100", "2026-09", "admin@billing.com");

        assertNotNull(regenerated);
        // Overridden values MUST be retained
        assertEquals(BigDecimal.valueOf(40000), regenerated.getBasicSalary());
        assertEquals(BigDecimal.valueOf(12000), regenerated.getHra());
        // Non-overridden allowance retains calculated structure value
        assertEquals(new BigDecimal("5000.00"), regenerated.getAllowances());
        // Gross = 40000 + 12000 + 5000 = 57000
        assertEquals(new BigDecimal("57000.00"), regenerated.getGrossSalary());
        // Deductions = pf(1800) + profTax(200) + lop(0) = 2000
        assertEquals(new BigDecimal("2000.00"), regenerated.getTotalDeductions());
        // Net = 57000 - 2000 = 55000
        assertEquals(new BigDecimal("55000.00"), regenerated.getNetSalary());

        // Baseline calculated values are tracked and distinguished
        assertNotNull(regenerated.getCalculatedValues());
        assertEquals(new BigDecimal("30000.00"), regenerated.getCalculatedValues().get("basicSalary"));
        assertEquals(new BigDecimal("10000.00"), regenerated.getCalculatedValues().get("hra"));
    }

    @Test
    void testApprovedLopDeductedInPayroll() {
        SalaryStructure structure = SalaryStructure.builder()
                .employeeId("EMP100")
                .basicSalary(BigDecimal.valueOf(26000)) // 26 working days in Sept 2026 (30 - 4 Sundays) -> ₹1000/day
                .hra(BigDecimal.valueOf(5000))
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

        // 2 approved LOP days on Sept 14 and Sept 15
        List<AttendanceRecord> records = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            LocalDate date = LocalDate.of(2026, 9, d);
            if (d == 14 || d == 15) {
                records.add(AttendanceRecord.builder()
                        .employeeId("EMP100")
                        .date(date)
                        .status(AttendanceRecord.AttendanceStatus.LEAVE)
                        .leaveType(LeaveRequest.LeaveType.LOP)
                        .isLop(true)
                        .remarks("Approved LOP")
                        .build());
            } else if (date.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                records.add(AttendanceRecord.builder()
                        .employeeId("EMP100")
                        .date(date)
                        .status(AttendanceRecord.AttendanceStatus.PRESENT)
                        .build());
            }
        }

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(structure));
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(records);
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRecord result = payrollService.processPayrollForEmployee("EMP100", "2026-09", "admin@billing.com");

        assertNotNull(result);
        assertEquals(2.0, result.getLopDays());
        // 26000 / 26 * 2 = 2000 LOP deduction
        assertEquals(new BigDecimal("2000.00"), result.getLopAmount());
        assertEquals(new BigDecimal("31000.00"), result.getGrossSalary()); // 26000 + 5000
        assertEquals(new BigDecimal("2000.00"), result.getTotalDeductions());
        assertEquals(new BigDecimal("29000.00"), result.getNetSalary()); // 31000 - 2000
    }

    @Test
    void testPaidLeaveDoesNotCauseSalaryDeduction() {
        SalaryStructure structure = SalaryStructure.builder()
                .employeeId("EMP100")
                .basicSalary(BigDecimal.valueOf(26000))
                .hra(BigDecimal.valueOf(5000))
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

        // 3 days of approved paid CASUAL_LEAVE
        List<AttendanceRecord> records = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            LocalDate date = LocalDate.of(2026, 9, d);
            if (d >= 14 && d <= 16) {
                records.add(AttendanceRecord.builder()
                        .employeeId("EMP100")
                        .date(date)
                        .status(AttendanceRecord.AttendanceStatus.LEAVE)
                        .leaveType(LeaveRequest.LeaveType.CASUAL_LEAVE)
                        .isLop(false)
                        .remarks("Casual Leave")
                        .build());
            } else if (date.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                records.add(AttendanceRecord.builder()
                        .employeeId("EMP100")
                        .date(date)
                        .status(AttendanceRecord.AttendanceStatus.PRESENT)
                        .build());
            }
        }

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(structure));
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(records);
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRecord result = payrollService.processPayrollForEmployee("EMP100", "2026-09", "admin@billing.com");

        assertNotNull(result);
        assertEquals(0.0, result.getLopDays());
        assertEquals(3, result.getLeaveDays());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getLopAmount()));
        // Net salary has zero LOP deduction
        assertEquals(new BigDecimal("31000.00"), result.getNetSalary());
    }

    @Test
    void testNoDuplicateLopDeductions() {
        SalaryStructure structure = SalaryStructure.builder()
                .employeeId("EMP100")
                .basicSalary(BigDecimal.valueOf(26000))
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

        // Sept 14 has both an AttendanceRecord marked as LEAVE (isLop=true) AND an approved LeaveRequest for LOP
        AttendanceRecord ar = AttendanceRecord.builder()
                .employeeId("EMP100")
                .date(LocalDate.of(2026, 9, 14))
                .status(AttendanceRecord.AttendanceStatus.LEAVE)
                .leaveType(LeaveRequest.LeaveType.LOP)
                .isLop(true)
                .build();

        LeaveRequest lr = LeaveRequest.builder()
                .employeeId("EMP100")
                .leaveType(LeaveRequest.LeaveType.LOP)
                .status(LeaveRequest.LeaveStatus.APPROVED)
                .fromDate(LocalDate.of(2026, 9, 14))
                .toDate(LocalDate.of(2026, 9, 14))
                .build();

        List<AttendanceRecord> records = new ArrayList<>();
        records.add(ar);
        for (int d = 1; d <= 30; d++) {
            if (d == 14) continue;
            LocalDate date = LocalDate.of(2026, 9, d);
            if (date.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                records.add(AttendanceRecord.builder()
                        .employeeId("EMP100")
                        .date(date)
                        .status(AttendanceRecord.AttendanceStatus.PRESENT)
                        .build());
            }
        }

        when(employeeRepository.findById("EMP100")).thenReturn(Optional.of(employee));
        when(salaryStructureRepository.findByEmployeeId("EMP100")).thenReturn(Optional.of(structure));
        when(companySettingsService.getSettings()).thenReturn(settings);
        when(attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(eq("EMP100"), any(), any())).thenReturn(records);
        when(leaveRequestRepository.findByEmployeeIdAndStatus("EMP100", LeaveRequest.LeaveStatus.APPROVED)).thenReturn(List.of(lr));
        when(payrollRepository.findByEmployeeIdAndPayrollMonth("EMP100", "2026-09")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(PayrollRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeAdvanceAccountRepository.findByEmployeeId("EMP100")).thenReturn(Optional.empty());
        when(employeeAdvanceAccountRepository.save(any(EmployeeAdvanceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRecord result = payrollService.processPayrollForEmployee("EMP100", "2026-09", "admin@billing.com");

        assertNotNull(result);
        // Sept 14 is evaluated only once on the calendar: lopDays must be 1.0, NOT 2.0
        assertEquals(1.0, result.getLopDays());
        assertEquals(new BigDecimal("1000.00"), result.getLopAmount());
    }
}

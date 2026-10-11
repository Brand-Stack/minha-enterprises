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
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AdvanceTransaction;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.Employee;
import com.app.billing.model.EmployeeAdvanceAccount;
import com.app.billing.model.EmployeeBonus;
import com.app.billing.model.PayrollRecord;
import com.app.billing.model.SalaryStructure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollCalculationService {

    private final SalaryStructureRepository salaryStructureRepository;
    private final EmployeeAdvanceAccountRepository employeeAdvanceAccountRepository;
    private final AdvanceTransactionRepository advanceTransactionRepository;
    private final EmployeeBonusRepository bonusRepository;
    private final PayrollRecordRepository payrollRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final OvertimeRecordRepository overtimeRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanySettingsService companySettingsService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // --- SALARY STRUCTURE ---
    public SalaryStructure saveSalaryStructure(SalaryStructure structure) {
        Employee employee = employeeRepository.findById(structure.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + structure.getEmployeeId()));

        structure.setEmployeeCode(employee.getEmployeeCode());
        structure.setEmployeeName(employee.getEmployeeName());
        structure.setActive(true);

        Optional<SalaryStructure> existingOpt = salaryStructureRepository.findByEmployeeId(structure.getEmployeeId());
        if (existingOpt.isPresent()) {
            structure.setId(existingOpt.get().getId());
        }

        SalaryStructure saved = salaryStructureRepository.save(structure);

        try {
            List<PayrollRecord> records = payrollRepository.findByEmployeeIdOrderByPayrollMonthDesc(saved.getEmployeeId());
            for (PayrollRecord pr : records) {
                if (!Boolean.TRUE.equals(pr.getIsLocked())) {
                    pr.setBasicSalary(saved.getBasicSalary() != null ? saved.getBasicSalary() : BigDecimal.ZERO);
                    pr.setHra(saved.getHra() != null ? saved.getHra() : BigDecimal.ZERO);
                    pr.setAllowances(saved.getAllowances() != null ? saved.getAllowances() : BigDecimal.ZERO);
                    pr.setPfAmount(saved.getPfDeduction() != null ? saved.getPfDeduction() : BigDecimal.ZERO);
                    pr.setProfessionalTax(saved.getProfessionalTax() != null ? saved.getProfessionalTax() : BigDecimal.ZERO);
                    pr.setTaxAmount(saved.getIncomeTax() != null ? saved.getIncomeTax() : BigDecimal.ZERO);

                    BigDecimal gross = pr.getBasicSalary().add(pr.getHra()).add(pr.getAllowances())
                            .add(pr.getOvertimeAmount() != null ? pr.getOvertimeAmount() : BigDecimal.ZERO)
                            .add(pr.getBonusAmount() != null ? pr.getBonusAmount() : BigDecimal.ZERO)
                            .add(pr.getYearlyBonusAmount() != null ? pr.getYearlyBonusAmount() : BigDecimal.ZERO)
                            .add(pr.getOtherEarnings() != null ? pr.getOtherEarnings() : BigDecimal.ZERO);

                    BigDecimal deductions = pr.getPfAmount().add(pr.getProfessionalTax()).add(pr.getTaxAmount())
                            .add(pr.getLopAmount() != null ? pr.getLopAmount() : BigDecimal.ZERO)
                            .add(pr.getAdvanceDeductionAmount() != null ? pr.getAdvanceDeductionAmount() : BigDecimal.ZERO)
                            .add(pr.getOtherDeductions() != null ? pr.getOtherDeductions() : BigDecimal.ZERO);

                    pr.setGrossSalary(gross);
                    pr.setTotalEarnings(gross);
                    pr.setTotalDeductions(deductions);
                    pr.setNetSalary(gross.subtract(deductions).max(BigDecimal.ZERO));
                    payrollRepository.save(pr);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync updated salary structure to draft payroll records: {}", e.getMessage());
        }

        auditService.log("SAVE_SALARY_STRUCTURE", "PAYROLL", saved.getId(), "Saved salary structure for " + employee.getEmployeeName());
        return saved;
    }

    public SalaryStructure getSalaryStructure(String employeeId) {
        return salaryStructureRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> {
                    Employee employee = employeeRepository.findById(employeeId).orElse(null);
                    String empCode = employee != null ? employee.getEmployeeCode() : null;
                    String empName = employee != null ? employee.getEmployeeName() : null;

                    Optional<PayrollRecord> latestRecord = payrollRepository.findByPayrollMonth(YearMonth.now().toString()).stream()
                            .filter(r -> employeeId.equals(r.getEmployeeId()))
                            .findFirst();

                    if (latestRecord.isEmpty()) {
                        latestRecord = payrollRepository.findFirstByEmployeeIdAndPayrollMonthLessThanOrderByPayrollMonthDesc(employeeId, YearMonth.now().toString());
                    }

                    BigDecimal basic = BigDecimal.valueOf(30000);
                    BigDecimal hra = BigDecimal.valueOf(10000);
                    BigDecimal allowances = BigDecimal.valueOf(5000);
                    BigDecimal pf = BigDecimal.valueOf(1800);
                    BigDecimal pt = BigDecimal.valueOf(200);
                    BigDecimal tax = BigDecimal.ZERO;

                    if (latestRecord.isPresent()) {
                        PayrollRecord pr = latestRecord.get();
                        if (pr.getBasicSalary() != null) basic = pr.getBasicSalary();
                        if (pr.getHra() != null) hra = pr.getHra();
                        if (pr.getAllowances() != null) allowances = pr.getAllowances();
                        if (pr.getPfAmount() != null) pf = pr.getPfAmount();
                        if (pr.getProfessionalTax() != null) pt = pr.getProfessionalTax();
                        if (pr.getTaxAmount() != null) tax = pr.getTaxAmount();
                    }

                    SalaryStructure newStruct = SalaryStructure.builder()
                            .employeeId(employeeId)
                            .employeeCode(empCode)
                            .employeeName(empName)
                            .basicSalary(basic)
                            .hra(hra)
                            .allowances(allowances)
                            .pfDeduction(pf)
                            .professionalTax(pt)
                            .incomeTax(tax)
                            .effectiveFrom(LocalDate.now().withDayOfMonth(1))
                            .active(true)
                            .build();

                    try {
                        SalaryStructure saved = salaryStructureRepository.save(newStruct);
                        return saved != null ? saved : newStruct;
                    } catch (Exception e) {
                        return newStruct;
                    }
                });
    }

    // --- SINGLE EMPLOYEE ADVANCE ACCOUNT & TRANSACTION LEDGER ---
    @Transactional
    public EmployeeAdvanceAccount getOrCreateAdvanceAccount(String employeeId) {
        return employeeAdvanceAccountRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> {
                    Employee employee = employeeRepository.findById(employeeId)
                            .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));
                    EmployeeAdvanceAccount newAccount = EmployeeAdvanceAccount.builder()
                            .employeeId(employee.getId())
                            .employeeCode(employee.getEmployeeCode())
                            .employeeName(employee.getEmployeeName())
                            .totalAdvanceGiven(BigDecimal.ZERO)
                            .totalRepaid(BigDecimal.ZERO)
                            .totalPayrollDeducted(BigDecimal.ZERO)
                            .outstandingBalance(BigDecimal.ZERO)
                            .status("ACTIVE")
                            .build();
                    return employeeAdvanceAccountRepository.save(newAccount);
                });
    }

    public EmployeeAdvanceAccount getAdvanceAccount(String employeeId) {
        return recalculateAdvanceAccountLedger(employeeId);
    }

    public List<AdvanceTransaction> getAdvanceTransactions(String employeeId) {
        List<AdvanceTransaction> list = advanceTransactionRepository.findByEmployeeIdOrderByTransactionDateDescCreatedAtDesc(employeeId);
        Employee emp = null;
        for (AdvanceTransaction tx : list) {
            if ((tx.getEmployeeName() == null || tx.getEmployeeName().isBlank() || tx.getEmployeeCode() == null || tx.getEmployeeCode().isBlank()) && tx.getEmployeeId() != null) {
                if (emp == null) {
                    emp = employeeRepository.findById(tx.getEmployeeId()).orElse(null);
                }
                if (emp != null) {
                    if (tx.getEmployeeName() == null || tx.getEmployeeName().isBlank()) {
                        tx.setEmployeeName(emp.getEmployeeName());
                    }
                    if (tx.getEmployeeCode() == null || tx.getEmployeeCode().isBlank()) {
                        tx.setEmployeeCode(emp.getEmployeeCode());
                    }
                    advanceTransactionRepository.save(tx);
                }
            }
        }
        return list;
    }

    public BigDecimal calculateOutstandingBalance(String employeeId) {
        EmployeeAdvanceAccount account = getOrCreateAdvanceAccount(employeeId);
        return account.getOutstandingBalance() != null ? account.getOutstandingBalance() : BigDecimal.ZERO;
    }

    /**
     * Recalculates the entire running balance and total summaries for an employee's advance account
     * to ensure 100% financial consistency across edits, deletions, and additions.
     */
    @Transactional
    public EmployeeAdvanceAccount recalculateAdvanceAccountLedger(String employeeId) {
        EmployeeAdvanceAccount account = getOrCreateAdvanceAccount(employeeId);
        List<AdvanceTransaction> transactions = advanceTransactionRepository.findByEmployeeIdOrderByTransactionDateDescCreatedAtDesc(employeeId);

        List<AdvanceTransaction> sortedTxs = transactions.stream()
                .filter(tx -> !"VOIDED".equalsIgnoreCase(tx.getStatus()))
                .sorted(Comparator.comparing(AdvanceTransaction::getTransactionDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(AdvanceTransaction::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        BigDecimal runningBal = BigDecimal.ZERO;
        BigDecimal totalGiven = BigDecimal.ZERO;
        BigDecimal totalRepaid = BigDecimal.ZERO;
        BigDecimal totalPayrollDeducted = BigDecimal.ZERO;

        for (AdvanceTransaction tx : sortedTxs) {
            BigDecimal prev = runningBal;
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;

            if (tx.getTransactionType() == AdvanceTransaction.TransactionType.ADVANCE_GIVEN) {
                totalGiven = totalGiven.add(amt);
                runningBal = runningBal.add(amt);
            } else if (tx.getTransactionType() == AdvanceTransaction.TransactionType.REPAYMENT) {
                totalRepaid = totalRepaid.add(amt);
                runningBal = runningBal.subtract(amt).max(BigDecimal.ZERO);
            } else if (tx.getTransactionType() == AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION) {
                totalPayrollDeducted = totalPayrollDeducted.add(amt);
                runningBal = runningBal.subtract(amt).max(BigDecimal.ZERO);
            } else if (tx.getTransactionType() == AdvanceTransaction.TransactionType.ADJUSTMENT) {
                runningBal = runningBal.add(amt);
                if (runningBal.compareTo(BigDecimal.ZERO) < 0) runningBal = BigDecimal.ZERO;
            }

            tx.setPreviousBalance(prev);
            tx.setResultingBalance(runningBal);
            advanceTransactionRepository.save(tx);
        }

        account.setTotalAdvanceGiven(totalGiven);
        account.setTotalRepaid(totalRepaid);
        account.setTotalPayrollDeducted(totalPayrollDeducted);
        account.setOutstandingBalance(runningBal);
        return employeeAdvanceAccountRepository.save(account);
    }

    @Transactional
    public AdvanceTransaction addAdvanceTransaction(String employeeId, AdvanceTransaction request, String operatorUsername) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
        if (request.getTransactionType() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }

        EmployeeAdvanceAccount account = getOrCreateAdvanceAccount(employeeId);
        BigDecimal currentBal = account.getOutstandingBalance() != null ? account.getOutstandingBalance() : BigDecimal.ZERO;

        // Repayment validation: amount <= outstanding balance
        if (request.getTransactionType() == AdvanceTransaction.TransactionType.REPAYMENT) {
            if (request.getAmount().compareTo(currentBal) > 0) {
                throw new IllegalArgumentException("Repayment amount (₹" + request.getAmount() +
                        ") cannot exceed outstanding advance balance (₹" + currentBal + ")");
            }
        }

        request.setAdvanceAccountId(account.getId());
        request.setEmployeeId(employeeId);
        request.setEmployeeCode(account.getEmployeeCode());
        request.setEmployeeName(account.getEmployeeName());
        if (request.getTransactionDate() == null) {
            request.setTransactionDate(LocalDate.now());
        }
        request.setCreatedBy(operatorUsername);
        request.setCreatedAt(LocalDateTime.now());
        request.setStatus("COMPLETED");

        AdvanceTransaction savedTx = advanceTransactionRepository.save(request);

        // Recalculate ledger to update account totals reliably
        EmployeeAdvanceAccount updatedAccount = recalculateAdvanceAccountLedger(employeeId);

        auditService.log("ADD_ADVANCE_TRANSACTION", "PAYROLL", savedTx.getId(),
                "Recorded " + request.getTransactionType() + " of ₹" + request.getAmount() + " for " + account.getEmployeeName());

        // Notifications
        try {
            if (request.getTransactionType() == AdvanceTransaction.TransactionType.ADVANCE_GIVEN) {
                String paymentInfo = request.getPaymentMode() != null ? request.getPaymentMode() : "Cash";
                if ("Other".equalsIgnoreCase(paymentInfo) && request.getOtherPaymentModeDetails() != null) {
                    paymentInfo += " (" + request.getOtherPaymentModeDetails() + ")";
                }
                notificationService.sendNotification(
                        employeeId,
                        com.app.billing.model.Notification.NotificationType.SALARY_ADVANCE_APPROVED,
                        "Salary Advance Added",
                        "An advance amount of ₹" + request.getAmount() + " has been recorded for you. Payment Mode: " +
                                paymentInfo + ". Date: " + request.getTransactionDate() + ". Outstanding Balance: ₹" + updatedAccount.getOutstandingBalance() + ".",
                        com.app.billing.security.Modules.PAYROLL,
                        "EmployeeAdvanceAccount",
                        account.getId(),
                        "/attendance/payroll",
                        com.app.billing.model.Notification.NotificationPriority.HIGH,
                        java.util.Map.of("amount", request.getAmount().toString(), "balance", updatedAccount.getOutstandingBalance().toString())
                );
            } else if (request.getTransactionType() == AdvanceTransaction.TransactionType.REPAYMENT) {
                notificationService.sendNotification(
                        employeeId,
                        com.app.billing.model.Notification.NotificationType.SALARY_ADVANCE_APPROVED,
                        "Advance Repayment Recorded",
                        "Repayment of ₹" + request.getAmount() + " recorded via " + (request.getPaymentMode() != null ? request.getPaymentMode() : "Direct") +
                                ". Remaining outstanding balance: ₹" + updatedAccount.getOutstandingBalance() + ".",
                        com.app.billing.security.Modules.PAYROLL,
                        "EmployeeAdvanceAccount",
                        account.getId(),
                        "/attendance/payroll",
                        com.app.billing.model.Notification.NotificationPriority.NORMAL,
                        java.util.Map.of("amount", request.getAmount().toString(), "balance", updatedAccount.getOutstandingBalance().toString())
                );
            }
        } catch (Exception e) {
            log.error("Failed to send advance transaction notification: {}", e.getMessage());
        }

        return savedTx;
    }

    @Transactional
    public AdvanceTransaction updateAdvanceTransaction(String transactionId, AdvanceTransaction updated, String operatorUsername) {
        AdvanceTransaction existing = advanceTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));

        if (updated.getAmount() != null && updated.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            existing.setAmount(updated.getAmount());
        }
        if (updated.getTransactionDate() != null) {
            existing.setTransactionDate(updated.getTransactionDate());
        }
        existing.setPaymentMode(updated.getPaymentMode());
        existing.setOtherPaymentModeDetails(updated.getOtherPaymentModeDetails());
        existing.setReferenceNumber(updated.getReferenceNumber());
        existing.setDescription(updated.getDescription());
        existing.setReason(updated.getReason());
        existing.setRemarks(updated.getRemarks());
        existing.setNotes(updated.getNotes());
        existing.setUpdatedBy(operatorUsername);
        existing.setUpdatedAt(LocalDateTime.now());

        AdvanceTransaction saved = advanceTransactionRepository.save(existing);
        recalculateAdvanceAccountLedger(existing.getEmployeeId());

        auditService.log("UPDATE_ADVANCE_TRANSACTION", "PAYROLL", transactionId,
                "Updated " + existing.getTransactionType() + " transaction by " + operatorUsername);

        return saved;
    }

    @Transactional
    public void deleteAdvanceTransaction(String transactionId, String operatorUsername) {
        AdvanceTransaction existing = advanceTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));

        String employeeId = existing.getEmployeeId();
        advanceTransactionRepository.delete(existing);

        recalculateAdvanceAccountLedger(employeeId);

        auditService.log("DELETE_ADVANCE_TRANSACTION", "PAYROLL", transactionId,
                "Deleted " + existing.getTransactionType() + " transaction of ₹" + existing.getAmount() + " by " + operatorUsername);
    }

    // --- BONUSES ---
    public EmployeeBonus addBonus(EmployeeBonus bonus, String adminUsername) {
        Employee employee = employeeRepository.findById(bonus.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + bonus.getEmployeeId()));

        bonus.setEmployeeCode(employee.getEmployeeCode());
        bonus.setEmployeeName(employee.getEmployeeName());
        bonus.setApprovedBy(adminUsername);
        bonus.setApprovedDate(LocalDateTime.now());

        EmployeeBonus saved = bonusRepository.save(bonus);
        auditService.log("ADD_BONUS", "BONUS", saved.getId(),
                "Added bonus ₹" + bonus.getAmount() + " (" + bonus.getBonusType() + ") for " + employee.getEmployeeName());

        try {
            notificationService.sendNotification(
                    bonus.getEmployeeId(),
                    com.app.billing.model.Notification.NotificationType.BONUS_ADDED,
                    "Bonus Added",
                    "A bonus of ₹" + bonus.getAmount() + " (" + bonus.getBonusType() + ") has been added to your payroll for " + bonus.getPayrollMonth() + ".",
                    com.app.billing.security.Modules.PAYROLL,
                    "EmployeeBonus",
                    saved.getId(),
                    "/attendance/payroll",
                    com.app.billing.model.Notification.NotificationPriority.NORMAL,
                    java.util.Map.of("amount", bonus.getAmount().toString(), "month", bonus.getPayrollMonth())
            );
        } catch (Exception e) {
            log.error("Failed to send bonus notification: {}", e.getMessage());
        }

        return saved;
    }

    public List<EmployeeBonus> getBonusesForEmployee(String employeeId) {
        return bonusRepository.findByEmployeeIdOrderByPaymentDateDesc(employeeId);
    }

    public List<EmployeeBonus> getAllBonuses() {
        return bonusRepository.findAll();
    }

    // --- PAYROLL PROCESSING & PAYSLIP GENERATION ---
    public PayrollRecord processPayrollForEmployee(String employeeId, String payrollMonth, String adminUsername) {
        return processPayrollForEmployee(employeeId, payrollMonth, null, adminUsername);
    }

    @Transactional
    public PayrollRecord processPayrollForEmployee(String employeeId, String payrollMonth, BigDecimal customAdvanceDeduction, String adminUsername) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

        YearMonth ym = YearMonth.parse(payrollMonth);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        int totalDaysInMonth = ym.lengthOfMonth();

        CompanySettingsDto settings = companySettingsService.getSettings();
        SalaryStructure structure = getSalaryStructure(employeeId);

        // Fetch attendance records
        List<AttendanceRecord> attendanceRecords = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, startDate, endDate);

        int presentDays = 0;
        int leaveDays = 0;
        double lopDays = 0.0;
        double totalOvertimeHours = 0.0;

        for (AttendanceRecord ar : attendanceRecords) {
            if (ar.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.LATE
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.PERMISSION
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.WORK_FROM_HOME
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.OVERTIME) {
                presentDays++;
            } else if (ar.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY) {
                presentDays++;
                lopDays += 0.5;
            } else if (ar.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE) {
                leaveDays++;
            } else if (ar.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT) {
                lopDays += 1.0;
            }

            if (ar.getOvertimeHours() != null && ar.getOvertimeHours() > 0) {
                totalOvertimeHours += ar.getOvertimeHours();
            }
        }

        // Account for absent unrecorded days
        int recordedDaysCount = attendanceRecords.size();
        if (recordedDaysCount < totalDaysInMonth) {
            for (int day = 1; day <= totalDaysInMonth; day++) {
                LocalDate d = ym.atDay(day);
                boolean recorded = attendanceRecords.stream().anyMatch(ar -> ar.getDate().equals(d));
                if (!recorded) {
                    String dayName = d.getDayOfWeek().name();
                    boolean isWeekend = settings.getWeekendDays() != null && settings.getWeekendDays().contains(dayName);
                    if (!isWeekend) {
                        lopDays += 1.0;
                    }
                }
            }
        }

        int sundayCount = 0;
        for (int day = 1; day <= totalDaysInMonth; day++) {
            if (ym.atDay(day).getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                sundayCount++;
            }
        }
        int workingDaysInMonth = Math.max(1, totalDaysInMonth - sundayCount);

        // LOP Amount: Calculated strictly on Basic Salary / Working Days * LOP Days
        BigDecimal basicSalaryStructure = structure.getBasicSalary() != null ? structure.getBasicSalary() : BigDecimal.ZERO;
        BigDecimal dailyRate = basicSalaryStructure.divide(BigDecimal.valueOf(workingDaysInMonth), 2, RoundingMode.HALF_UP);
        BigDecimal computedLopAmount = dailyRate.multiply(BigDecimal.valueOf(lopDays)).setScale(2, RoundingMode.HALF_UP);

        // Overtime Amount
        double otMultiplier = settings.getOvertimeHourlyRateMultiplier() != null ? settings.getOvertimeHourlyRateMultiplier() : 1.5;
        double expectedHoursPerDay = settings.getWorkingHoursPerDay() != null ? settings.getWorkingHoursPerDay() : 8.0;
        BigDecimal hourlyRate = dailyRate.divide(BigDecimal.valueOf(expectedHoursPerDay), 2, RoundingMode.HALF_UP);
        BigDecimal computedOvertimeAmount = hourlyRate.multiply(BigDecimal.valueOf(totalOvertimeHours * otMultiplier)).setScale(2, RoundingMode.HALF_UP);

        // Bonuses
        List<EmployeeBonus> monthBonuses = bonusRepository.findByEmployeeIdAndPayrollMonth(employeeId, payrollMonth);
        BigDecimal computedBonus = BigDecimal.ZERO;
        BigDecimal computedYearlyBonus = BigDecimal.ZERO;

        for (EmployeeBonus bonus : monthBonuses) {
            if (bonus.getBonusType() == EmployeeBonus.BonusType.YEARLY_BONUS) {
                computedYearlyBonus = computedYearlyBonus.add(bonus.getAmount());
            } else {
                computedBonus = computedBonus.add(bonus.getAmount());
            }
        }

        String payslipNo = "PAY-" + payrollMonth.replace("-", "") + "-" + (employee.getEmployeeCode() != null ? employee.getEmployeeCode() : employee.getId());

        // Handle existing payslip & reverse any previous advance deduction before re-processing
        Optional<PayrollRecord> existingRecordOpt = payrollRepository.findByEmployeeIdAndPayrollMonth(employeeId, payrollMonth);
        Optional<PayrollRecord> priorRecordOpt = existingRecordOpt.isPresent()
                ? Optional.empty()
                : Optional.ofNullable(payrollRepository.findFirstByEmployeeIdAndPayrollMonthLessThanOrderByPayrollMonthDesc(employeeId, payrollMonth)).orElse(Optional.empty());
        PayrollRecord priorRecord = priorRecordOpt.orElse(null);

        PayrollRecord record;
        if (existingRecordOpt.isPresent()) {
            record = existingRecordOpt.get();
            if (Boolean.TRUE.equals(record.getIsLocked())) {
                throw new IllegalStateException("Payroll for " + payrollMonth + " is locked and cannot be updated without unlocking.");
            }
            List<AdvanceTransaction> prevTxs = new ArrayList<>();
            if (record.getPayslipNumber() != null && !record.getPayslipNumber().isBlank()) {
                prevTxs.addAll(advanceTransactionRepository.findAllByPayslipIdAndTransactionType(record.getPayslipNumber(), AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION));
            }
            if (record.getId() != null && !record.getId().isBlank()) {
                List<AdvanceTransaction> txsById = advanceTransactionRepository.findAllByPayslipIdAndTransactionType(record.getId(), AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION);
                for (AdvanceTransaction tx : txsById) {
                    if (prevTxs.stream().noneMatch(t -> t.getId().equals(tx.getId()))) {
                        prevTxs.add(tx);
                    }
                }
            }
            for (AdvanceTransaction tx : prevTxs) {
                try {
                    deleteAdvanceTransaction(tx.getId(), adminUsername);
                } catch (Exception e) {
                    log.warn("Could not reverse previous advance transaction {} during payslip re-processing: {}", tx.getId(), e.getMessage());
                }
            }
        } else {
            record = PayrollRecord.builder()
                    .employeeId(employeeId)
                    .payrollMonth(payrollMonth)
                    .payslipNumber(payslipNo)
                    .build();
        }

        // Base Salary configuration from Salary Management (structure)
        BigDecimal basicSalary = (structure.getBasicSalary() != null ? structure.getBasicSalary() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hra = (structure.getHra() != null ? structure.getHra() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal allowances = (structure.getAllowances() != null ? structure.getAllowances() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal rawOtherEarnings = (existingRecordOpt.isPresent() && existingRecordOpt.get().getOtherEarnings() != null)
                ? existingRecordOpt.get().getOtherEarnings()
                : (priorRecord != null && priorRecord.getOtherEarnings() != null
                    ? priorRecord.getOtherEarnings() : BigDecimal.ZERO);
        BigDecimal otherEarnings = rawOtherEarnings != null ? rawOtherEarnings.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        // LOP calculation based on Basic Salary ONLY
        BigDecimal dailyRateResolved = basicSalary.divide(BigDecimal.valueOf(workingDaysInMonth), 2, RoundingMode.HALF_UP);
        BigDecimal computedLopAmountResolved = dailyRateResolved.multiply(BigDecimal.valueOf(lopDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hourlyRateResolved = dailyRateResolved.divide(BigDecimal.valueOf(expectedHoursPerDay), 2, RoundingMode.HALF_UP);
        BigDecimal computedOvertimeAmountResolved = hourlyRateResolved.multiply(BigDecimal.valueOf(totalOvertimeHours * otMultiplier)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal overtimeAmount;
        if (existingRecordOpt.isPresent() && existingRecordOpt.get().getOvertimeAmount() != null) {
            overtimeAmount = existingRecordOpt.get().getOvertimeAmount();
        } else if (computedOvertimeAmountResolved.compareTo(BigDecimal.ZERO) > 0) {
            overtimeAmount = computedOvertimeAmountResolved;
        } else if (priorRecord != null && priorRecord.getOvertimeAmount() != null) {
            overtimeAmount = priorRecord.getOvertimeAmount();
        } else {
            overtimeAmount = BigDecimal.ZERO;
        }

        BigDecimal totalBonus;
        if (existingRecordOpt.isPresent() && existingRecordOpt.get().getBonusAmount() != null && existingRecordOpt.get().getBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
            totalBonus = existingRecordOpt.get().getBonusAmount();
        } else if (computedBonus.compareTo(BigDecimal.ZERO) > 0) {
            totalBonus = computedBonus;
        } else if (priorRecord != null && priorRecord.getBonusAmount() != null) {
            totalBonus = priorRecord.getBonusAmount();
        } else {
            totalBonus = BigDecimal.ZERO;
        }

        BigDecimal totalYearlyBonus;
        if (existingRecordOpt.isPresent() && existingRecordOpt.get().getYearlyBonusAmount() != null && existingRecordOpt.get().getYearlyBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
            totalYearlyBonus = existingRecordOpt.get().getYearlyBonusAmount();
        } else if (computedYearlyBonus.compareTo(BigDecimal.ZERO) > 0) {
            totalYearlyBonus = computedYearlyBonus;
        } else if (priorRecord != null && priorRecord.getYearlyBonusAmount() != null) {
            totalYearlyBonus = priorRecord.getYearlyBonusAmount();
        } else {
            totalYearlyBonus = BigDecimal.ZERO;
        }

        BigDecimal pfAmount = (structure.getPfDeduction() != null ? structure.getPfDeduction() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profTax = (structure.getProfessionalTax() != null ? structure.getProfessionalTax() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal incomeTax = (structure.getIncomeTax() != null ? structure.getIncomeTax() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal lopAmount;
        if (existingRecordOpt.isPresent() && existingRecordOpt.get().getLopAmount() != null) {
            lopAmount = existingRecordOpt.get().getLopAmount();
        } else if (computedLopAmountResolved.compareTo(BigDecimal.ZERO) > 0) {
            lopAmount = computedLopAmountResolved;
        } else if (priorRecord != null && priorRecord.getLopAmount() != null) {
            lopAmount = priorRecord.getLopAmount();
        } else {
            lopAmount = BigDecimal.ZERO;
        }

        BigDecimal otherDeductions = (existingRecordOpt.isPresent() && existingRecordOpt.get().getOtherDeductions() != null)
                ? existingRecordOpt.get().getOtherDeductions()
                : (priorRecord != null && priorRecord.getOtherDeductions() != null
                    ? priorRecord.getOtherDeductions() : BigDecimal.ZERO);

        // Get current advance account balance (after reversing prior transactions for this month if re-processing)
        EmployeeAdvanceAccount advanceAccount = recalculateAdvanceAccountLedger(employeeId);
        BigDecimal outstandingBalance = advanceAccount.getOutstandingBalance() != null ? advanceAccount.getOutstandingBalance() : BigDecimal.ZERO;
        BigDecimal previousBalance = outstandingBalance;

        // Advance deduction calculation & validation
        BigDecimal advanceDeduction = BigDecimal.ZERO;
        if (outstandingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            advanceDeduction = BigDecimal.ZERO;
        } else if (customAdvanceDeduction != null) {
            if (customAdvanceDeduction.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Advance deduction amount cannot be negative");
            }
            if (customAdvanceDeduction.compareTo(outstandingBalance) > 0) {
                throw new IllegalArgumentException("Advance deduction (₹" + customAdvanceDeduction +
                        ") cannot exceed employee's outstanding advance balance of ₹" + outstandingBalance);
            }
            advanceDeduction = customAdvanceDeduction;
        } else {
            BigDecimal preliminaryEarnings = basicSalary.add(hra).add(allowances).add(overtimeAmount).add(totalBonus).add(totalYearlyBonus).add(otherEarnings);
            BigDecimal preliminaryDeductions = pfAmount.add(profTax).add(incomeTax).add(lopAmount).add(otherDeductions);
            BigDecimal availableNet = preliminaryEarnings.subtract(preliminaryDeductions).max(BigDecimal.ZERO);
            advanceDeduction = outstandingBalance.min(availableNet);
        }

        // Calculate Totals based on authoritative component values
        BigDecimal totalEarnings = basicSalary.add(hra).add(allowances).add(overtimeAmount).add(totalBonus).add(totalYearlyBonus).add(otherEarnings);
        BigDecimal totalDeductions = pfAmount.add(profTax).add(incomeTax).add(lopAmount).add(advanceDeduction).add(otherDeductions);
        BigDecimal grossSalary = totalEarnings;
        BigDecimal netSalary = totalEarnings.subtract(totalDeductions).max(BigDecimal.ZERO);

        // Record PAYROLL_DEDUCTION transaction in Advance Account ledger if advanceDeduction > 0
        if (advanceDeduction.compareTo(BigDecimal.ZERO) > 0) {
            AdvanceTransaction deductionTx = AdvanceTransaction.builder()
                    .advanceAccountId(advanceAccount.getId())
                    .employeeId(employeeId)
                    .employeeCode(employee.getEmployeeCode())
                    .employeeName(employee.getEmployeeName())
                    .transactionType(AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION)
                    .amount(advanceDeduction)
                    .transactionDate(LocalDate.now())
                    .paymentMode("Payroll Deduction")
                    .reason("Payroll Advance Deduction for " + payrollMonth)
                    .description("Monthly Payroll Advance Recovery (" + payrollMonth + ")")
                    .payrollMonth(payrollMonth)
                    .payslipId(payslipNo)
                    .createdBy(adminUsername)
                    .createdAt(LocalDateTime.now())
                    .status("COMPLETED")
                    .build();
            advanceTransactionRepository.save(deductionTx);
            advanceAccount = recalculateAdvanceAccountLedger(employeeId);
        }

        record.setAdvanceId(advanceAccount.getId());
        record.setOriginalAdvanceAmount(advanceAccount.getTotalAdvanceGiven());
        record.setPreviousAdvanceBalance(previousBalance);
        record.setRemainingAdvanceBalance(advanceAccount.getOutstandingBalance());
        record.setTotalAdvanceRecoveredSoFar((advanceAccount.getTotalPayrollDeducted() != null ? advanceAccount.getTotalPayrollDeducted() : BigDecimal.ZERO)
                .add(advanceAccount.getTotalRepaid() != null ? advanceAccount.getTotalRepaid() : BigDecimal.ZERO));

        record.setEmployeeCode(employee.getEmployeeCode());
        record.setEmployeeName(employee.getEmployeeName());
        record.setDepartment(employee.getCategory());
        record.setDesignation(employee.getDesignation());
        record.setJoiningDate(employee.getDateOfJoining());
        record.setTotalWorkingDays(workingDaysInMonth);
        record.setPresentDays(presentDays);
        record.setLeaveDays(leaveDays);
        record.setLopDays(lopDays);
        record.setOvertimeHours(totalOvertimeHours);
        record.setBasicSalary(basicSalary);
        record.setHra(hra);
        record.setAllowances(allowances);
        record.setOvertimeAmount(overtimeAmount);
        record.setBonusAmount(totalBonus);
        record.setYearlyBonusAmount(totalYearlyBonus);
        record.setOtherEarnings(otherEarnings);
        record.setPfAmount(pfAmount);
        record.setProfessionalTax(profTax);
        record.setTaxAmount(incomeTax);
        record.setLopAmount(lopAmount);
        record.setAdvanceDeductionAmount(advanceDeduction);
        record.setOtherDeductions(otherDeductions);
        record.setGrossSalary(grossSalary);
        record.setTotalEarnings(totalEarnings);
        record.setTotalDeductions(totalDeductions);
        record.setNetSalary(netSalary);
        record.setBankName(employee.getBankName());
        record.setAccountHolderName(employee.getAccountHolderName());
        record.setAccountNumber(employee.getAccountNumber());
        record.setIfscCode(employee.getIfscCode());
        record.setBankBranch(employee.getBankBranch());
        record.setStatus(PayrollRecord.PayrollStatus.GENERATED);
        record.setIsLocked(false);
        record.setGeneratedAt(LocalDateTime.now());
        record.setGeneratedBy(adminUsername);

        if (record.getAuditLogs() == null) {
            record.setAuditLogs(new ArrayList<>());
        }
        record.getAuditLogs().add(PayrollRecord.PayrollAuditEntry.builder()
                .timestamp(LocalDateTime.now())
                .action("GENERATE_PAYROLL")
                .modifiedBy(adminUsername)
                .remarks("Generated payslip for " + payrollMonth + " (Net Salary ₹" + netSalary +
                        ", Advance Deducted: ₹" + advanceDeduction + ")")
                .build());

        PayrollRecord saved = payrollRepository.save(record);
        auditService.log("PROCESS_PAYROLL", "PAYROLL", saved.getId(),
                "Processed payslip for " + employee.getEmployeeName() + " (" + payrollMonth + ") - Net Salary: ₹" + netSalary);

        // Notifications
        try {
            String msg = "Payslip for " + payrollMonth + " has been released.";
            if (advanceDeduction.compareTo(BigDecimal.ZERO) > 0) {
                msg += " ₹" + advanceDeduction + " deducted toward salary advance. Remaining balance: ₹" + advanceAccount.getOutstandingBalance() + ".";
            }
            notificationService.sendNotification(
                    employeeId,
                    com.app.billing.model.Notification.NotificationType.PAYSLIP_GENERATED,
                    "Payslip Released",
                    msg,
                    com.app.billing.security.Modules.PAYROLL,
                    "PayrollRecord",
                    saved.getId(),
                    "/attendance/payroll",
                    com.app.billing.model.Notification.NotificationPriority.NORMAL,
                    java.util.Map.of("payrollMonth", payrollMonth, "netSalary", netSalary.toString(), "advanceDeduction", advanceDeduction.toString())
            );
        } catch (Exception e) {
            log.error("Failed to send payslip notification: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional
    public PayrollRecord saveDraftPayroll(PayrollRecord draft, String adminUsername) {
        if (draft.getEmployeeId() == null || draft.getPayrollMonth() == null) {
            throw new IllegalArgumentException("Employee ID and Payroll Month are required");
        }

        Employee employee = employeeRepository.findById(draft.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + draft.getEmployeeId()));

        Optional<PayrollRecord> existingOpt = payrollRepository.findByEmployeeIdAndPayrollMonth(draft.getEmployeeId(), draft.getPayrollMonth());
        PayrollRecord record;
        if (existingOpt.isPresent()) {
            record = existingOpt.get();
            if (Boolean.TRUE.equals(record.getIsLocked())) {
                throw new IllegalStateException("Payroll for " + draft.getPayrollMonth() + " is locked and cannot be updated.");
            }
            // Reverse previous advance deduction before applying draft update
            if (record.getPayslipNumber() != null) {
                Optional<AdvanceTransaction> prevTxOpt = advanceTransactionRepository.findByPayslipIdAndTransactionType(record.getPayslipNumber(), AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION);
                if (prevTxOpt.isPresent()) {
                    deleteAdvanceTransaction(prevTxOpt.get().getId(), adminUsername);
                }
            }
        } else {
            String payslipNo = "PAY-" + draft.getPayrollMonth().replace("-", "") + "-" + (employee.getEmployeeCode() != null ? employee.getEmployeeCode() : employee.getId());
            record = PayrollRecord.builder()
                    .employeeId(draft.getEmployeeId())
                    .payrollMonth(draft.getPayrollMonth())
                    .payslipNumber(payslipNo)
                    .build();
        }

        // Merge updated fields from draft onto record
        if (draft.getBasicSalary() != null) record.setBasicSalary(draft.getBasicSalary());
        if (draft.getHra() != null) record.setHra(draft.getHra());
        if (draft.getAllowances() != null) record.setAllowances(draft.getAllowances());
        if (draft.getOtherEarnings() != null) record.setOtherEarnings(draft.getOtherEarnings());
        if (draft.getOvertimeAmount() != null) record.setOvertimeAmount(draft.getOvertimeAmount());
        if (draft.getBonusAmount() != null) record.setBonusAmount(draft.getBonusAmount());
        if (draft.getYearlyBonusAmount() != null) record.setYearlyBonusAmount(draft.getYearlyBonusAmount());

        if (draft.getPfAmount() != null) record.setPfAmount(draft.getPfAmount());
        if (draft.getProfessionalTax() != null) record.setProfessionalTax(draft.getProfessionalTax());
        if (draft.getTaxAmount() != null) record.setTaxAmount(draft.getTaxAmount());
        if (draft.getLopAmount() != null) record.setLopAmount(draft.getLopAmount());
        if (draft.getAdvanceDeductionAmount() != null) record.setAdvanceDeductionAmount(draft.getAdvanceDeductionAmount());
        if (draft.getOtherDeductions() != null) record.setOtherDeductions(draft.getOtherDeductions());

        if (draft.getTotalWorkingDays() != null) record.setTotalWorkingDays(draft.getTotalWorkingDays());
        if (draft.getPresentDays() != null) record.setPresentDays(draft.getPresentDays());
        if (draft.getLeaveDays() != null) record.setLeaveDays(draft.getLeaveDays());
        if (draft.getLopDays() != null) record.setLopDays(draft.getLopDays());
        if (draft.getOvertimeHours() != null) record.setOvertimeHours(draft.getOvertimeHours());

        if (record.getBasicSalary() != null && record.getBasicSalary().compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Basic salary cannot be negative");
        if (record.getBonusAmount() != null && record.getBonusAmount().compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Bonus amount cannot be negative");
        if (record.getOtherDeductions() != null && record.getOtherDeductions().compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Deductions cannot be negative");

        EmployeeAdvanceAccount advanceAccount = recalculateAdvanceAccountLedger(draft.getEmployeeId());
        BigDecimal outstandingBalance = advanceAccount.getOutstandingBalance() != null ? advanceAccount.getOutstandingBalance() : BigDecimal.ZERO;

        if (record.getAdvanceDeductionAmount() != null && record.getAdvanceDeductionAmount().compareTo(outstandingBalance) > 0) {
            throw new IllegalArgumentException("Advance deduction (₹" + record.getAdvanceDeductionAmount() +
                    ") cannot exceed outstanding advance balance of ₹" + outstandingBalance.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal advDeduct = record.getAdvanceDeductionAmount() != null ? record.getAdvanceDeductionAmount() : BigDecimal.ZERO;
        if (advDeduct.compareTo(BigDecimal.ZERO) > 0) {
            AdvanceTransaction deductionTx = AdvanceTransaction.builder()
                    .advanceAccountId(advanceAccount.getId())
                    .employeeId(draft.getEmployeeId())
                    .employeeCode(employee.getEmployeeCode())
                    .employeeName(employee.getEmployeeName())
                    .transactionType(AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION)
                    .amount(advDeduct)
                    .transactionDate(LocalDate.now())
                    .paymentMode("Payroll Deduction")
                    .reason("Payroll Advance Deduction for " + draft.getPayrollMonth())
                    .description("Monthly Payroll Advance Recovery (" + draft.getPayrollMonth() + ")")
                    .payrollMonth(draft.getPayrollMonth())
                    .payslipId(record.getPayslipNumber())
                    .createdBy(adminUsername)
                    .createdAt(LocalDateTime.now())
                    .status("COMPLETED")
                    .build();
            advanceTransactionRepository.save(deductionTx);
            advanceAccount = recalculateAdvanceAccountLedger(draft.getEmployeeId());
        }

        record.setAdvanceId(advanceAccount.getId());
        record.setOriginalAdvanceAmount(advanceAccount.getTotalAdvanceGiven());
        record.setRemainingAdvanceBalance(advanceAccount.getOutstandingBalance());
        record.setTotalAdvanceRecoveredSoFar((advanceAccount.getTotalPayrollDeducted() != null ? advanceAccount.getTotalPayrollDeducted() : BigDecimal.ZERO)
                .add(advanceAccount.getTotalRepaid() != null ? advanceAccount.getTotalRepaid() : BigDecimal.ZERO));

        BigDecimal basic = record.getBasicSalary() != null ? record.getBasicSalary() : BigDecimal.ZERO;
        BigDecimal hra = record.getHra() != null ? record.getHra() : BigDecimal.ZERO;
        BigDecimal allowances = record.getAllowances() != null ? record.getAllowances() : BigDecimal.ZERO;
        BigDecimal ot = record.getOvertimeAmount() != null ? record.getOvertimeAmount() : BigDecimal.ZERO;
        BigDecimal bonus = record.getBonusAmount() != null ? record.getBonusAmount() : BigDecimal.ZERO;
        BigDecimal yearlyBonus = record.getYearlyBonusAmount() != null ? record.getYearlyBonusAmount() : BigDecimal.ZERO;
        BigDecimal otherEarnings = record.getOtherEarnings() != null ? record.getOtherEarnings() : BigDecimal.ZERO;

        BigDecimal pf = record.getPfAmount() != null ? record.getPfAmount() : BigDecimal.ZERO;
        BigDecimal profTax = record.getProfessionalTax() != null ? record.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal tax = record.getTaxAmount() != null ? record.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal lop = record.getLopAmount() != null ? record.getLopAmount() : BigDecimal.ZERO;
        BigDecimal otherDed = record.getOtherDeductions() != null ? record.getOtherDeductions() : BigDecimal.ZERO;

        BigDecimal totalEarnings = basic.add(hra).add(allowances).add(ot).add(bonus).add(yearlyBonus).add(otherEarnings);
        BigDecimal totalDeductions = pf.add(profTax).add(tax).add(lop).add(advDeduct).add(otherDed);
        BigDecimal netSalary = totalEarnings.subtract(totalDeductions).max(BigDecimal.ZERO);

        record.setEmployeeCode(employee.getEmployeeCode());
        record.setEmployeeName(employee.getEmployeeName());
        record.setDepartment(employee.getCategory());
        record.setDesignation(employee.getDesignation());
        record.setGrossSalary(totalEarnings);
        record.setTotalEarnings(totalEarnings);
        record.setTotalDeductions(totalDeductions);
        record.setNetSalary(netSalary);
        if (record.getStatus() == null) {
            record.setStatus(PayrollRecord.PayrollStatus.DRAFT);
        }

        if (record.getAuditLogs() == null) {
            record.setAuditLogs(new ArrayList<>());
        }
        record.getAuditLogs().add(PayrollRecord.PayrollAuditEntry.builder()
                .timestamp(LocalDateTime.now())
                .action("SAVE_DRAFT")
                .modifiedBy(adminUsername)
                .remarks("Saved draft payroll for " + record.getPayrollMonth())
                .build());

        return payrollRepository.save(record);
    }

    @Transactional
    public PayrollRecord lockPayroll(String recordId, String adminUsername) {
        PayrollRecord record = payrollRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record not found with ID: " + recordId));

        record.setIsLocked(true);
        record.setStatus(PayrollRecord.PayrollStatus.LOCKED);
        record.setLockedAt(LocalDateTime.now());
        record.setLockedBy(adminUsername);

        if (record.getAuditLogs() == null) record.setAuditLogs(new ArrayList<>());
        record.getAuditLogs().add(PayrollRecord.PayrollAuditEntry.builder()
                .timestamp(LocalDateTime.now())
                .action("LOCK_PAYROLL")
                .modifiedBy(adminUsername)
                .remarks("Locked payroll record to prevent unauthorized edits")
                .build());

        return payrollRepository.save(record);
    }

    @Transactional
    public PayrollRecord unlockPayroll(String recordId, String adminUsername, String reason) {
        PayrollRecord record = payrollRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record not found with ID: " + recordId));

        record.setIsLocked(false);
        record.setStatus(PayrollRecord.PayrollStatus.DRAFT);

        if (record.getAuditLogs() == null) record.setAuditLogs(new ArrayList<>());
        record.getAuditLogs().add(PayrollRecord.PayrollAuditEntry.builder()
                .timestamp(LocalDateTime.now())
                .action("UNLOCK_PAYROLL")
                .modifiedBy(adminUsername)
                .remarks("Unlocked payroll record. Reason: " + (reason != null ? reason : "N/A"))
                .build());

        return payrollRepository.save(record);
    }

    @Transactional
    public List<PayrollRecord> processPayrollForAll(String payrollMonth, String adminUsername) {
        return batchProcessPayroll(payrollMonth, null, adminUsername);
    }

    @Transactional
    public List<PayrollRecord> batchProcessPayroll(String payrollMonth, Map<String, BigDecimal> customDeductionsMap, String adminUsername) {
        List<Employee> employees = employeeRepository.findAll();
        List<PayrollRecord> list = new ArrayList<>();
        for (Employee emp : employees) {
            if (emp.getStatus() == Employee.Status.ACTIVE) {
                try {
                    BigDecimal customDeduction = customDeductionsMap != null ? customDeductionsMap.get(emp.getId()) : null;
                    list.add(processPayrollForEmployee(emp.getId(), payrollMonth, customDeduction, adminUsername));
                } catch (Exception e) {
                    log.error("Failed to process payroll in batch for employee ID {}: {}", emp.getId(), e.getMessage());
                }
            }
        }
        return list;
    }

    public PayrollRecord getPayrollRecordById(String recordId) {
        return payrollRepository.findById(recordId)
                .or(() -> payrollRepository.findByPayslipNumber(recordId))
                .orElseThrow(() -> new ResourceNotFoundException("Payslip record not found with ID or number: " + recordId));
    }

    public PayrollRecord getPayrollRecord(String employeeId, String payrollMonth) {
        // First check if employeeId is actually a payslip record ID
        Optional<PayrollRecord> byIdOpt = payrollRepository.findById(employeeId);
        if (byIdOpt.isPresent()) {
            return byIdOpt.get();
        }

        // Try lookup by employeeId and payrollMonth
        Optional<PayrollRecord> recordOpt = payrollRepository.findByEmployeeIdAndPayrollMonth(employeeId, payrollMonth);
        if (recordOpt.isPresent()) {
            return recordOpt.get();
        }

        // Fallback: lookup by employeeCode and payrollMonth
        Optional<PayrollRecord> byCodeOpt = payrollRepository.findByPayrollMonth(payrollMonth).stream()
                .filter(r -> (r.getEmployeeId() != null && r.getEmployeeId().equalsIgnoreCase(employeeId))
                        || (r.getEmployeeCode() != null && r.getEmployeeCode().equalsIgnoreCase(employeeId))
                        || (r.getId() != null && r.getId().equalsIgnoreCase(employeeId)))
                .findFirst();
        if (byCodeOpt.isPresent()) {
            return byCodeOpt.get();
        }

        // On-the-fly preview fallback for draft payslips not yet saved in database
        Optional<Employee> empOpt = employeeRepository.findById(employeeId)
                .or(() -> employeeRepository.findByEmployeeCode(employeeId));

        if (empOpt.isPresent()) {
            try {
                return previewPayrollForEmployee(empOpt.get().getId(), payrollMonth);
            } catch (Exception e) {
                log.error("Failed to generate preview payslip for employee {}: {}", empOpt.get().getId(), e.getMessage());
            }
        }

        throw new ResourceNotFoundException("Payslip not found for employee/record " + employeeId + " and month " + payrollMonth);
    }

    public List<PayrollRecord> getPayrollsForEmployee(String employeeId) {
        return payrollRepository.findByEmployeeIdOrderByPayrollMonthDesc(employeeId);
    }

    public PayrollRecord previewPayrollForEmployee(String employeeId, String payrollMonth) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

        YearMonth ym = YearMonth.parse(payrollMonth);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        int totalDaysInMonth = ym.lengthOfMonth();

        CompanySettingsDto settings = companySettingsService.getSettings();
        SalaryStructure structure = getSalaryStructure(employeeId);

        List<AttendanceRecord> attendanceRecords = attendanceRecordRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, startDate, endDate);

        int presentDays = 0;
        int leaveDays = 0;
        double lopDays = 0.0;
        double totalOvertimeHours = 0.0;

        for (AttendanceRecord ar : attendanceRecords) {
            if (ar.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.LATE
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.EARLY_CHECKOUT
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.LATE_AND_EARLY_CHECKOUT
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.PERMISSION
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.WORK_FROM_HOME
                    || ar.getStatus() == AttendanceRecord.AttendanceStatus.OVERTIME) {
                presentDays++;
            } else if (ar.getStatus() == AttendanceRecord.AttendanceStatus.HALF_DAY) {
                presentDays++;
                lopDays += 0.5;
            } else if (ar.getStatus() == AttendanceRecord.AttendanceStatus.LEAVE) {
                leaveDays++;
            } else if (ar.getStatus() == AttendanceRecord.AttendanceStatus.ABSENT) {
                lopDays += 1.0;
            }

            if (ar.getOvertimeHours() != null && ar.getOvertimeHours() > 0) {
                totalOvertimeHours += ar.getOvertimeHours();
            }
        }

        int recordedDaysCount = attendanceRecords.size();
        if (recordedDaysCount < totalDaysInMonth) {
            for (int day = 1; day <= totalDaysInMonth; day++) {
                LocalDate d = ym.atDay(day);
                boolean recorded = attendanceRecords.stream().anyMatch(ar -> ar.getDate().equals(d));
                if (!recorded) {
                    String dayName = d.getDayOfWeek().name();
                    boolean isWeekend = settings.getWeekendDays() != null && settings.getWeekendDays().contains(dayName);
                    if (!isWeekend) {
                        lopDays += 1.0;
                    }
                }
            }
        }

        int sundayCount = 0;
        for (int day = 1; day <= totalDaysInMonth; day++) {
            if (ym.atDay(day).getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                sundayCount++;
            }
        }
        int workingDaysInMonth = Math.max(1, totalDaysInMonth - sundayCount);

        BigDecimal basicSalaryStructure = structure.getBasicSalary() != null ? structure.getBasicSalary() : BigDecimal.ZERO;

        double otMultiplier = settings.getOvertimeHourlyRateMultiplier() != null ? settings.getOvertimeHourlyRateMultiplier() : 1.5;
        double expectedHoursPerDay = settings.getWorkingHoursPerDay() != null ? settings.getWorkingHoursPerDay() : 8.0;

        List<EmployeeBonus> monthBonuses = bonusRepository.findByEmployeeIdAndPayrollMonth(employeeId, payrollMonth);
        BigDecimal computedBonus = BigDecimal.ZERO;
        BigDecimal computedYearlyBonus = BigDecimal.ZERO;
        for (EmployeeBonus bonus : monthBonuses) {
            if (bonus.getBonusType() == EmployeeBonus.BonusType.YEARLY_BONUS) {
                computedYearlyBonus = computedYearlyBonus.add(bonus.getAmount());
            } else {
                computedBonus = computedBonus.add(bonus.getAmount());
            }
        }

        String payslipNo = "PAY-" + payrollMonth.replace("-", "") + "-" + (employee.getEmployeeCode() != null ? employee.getEmployeeCode() : employee.getId());

        Optional<PayrollRecord> priorRecordOpt = Optional.ofNullable(payrollRepository.findFirstByEmployeeIdAndPayrollMonthLessThanOrderByPayrollMonthDesc(employeeId, payrollMonth)).orElse(Optional.empty());
        PayrollRecord priorRecord = priorRecordOpt.orElse(null);

        BigDecimal basicSalary = structure.getBasicSalary() != null ? structure.getBasicSalary() : BigDecimal.ZERO;
        BigDecimal hra = structure.getHra() != null ? structure.getHra() : BigDecimal.ZERO;
        BigDecimal allowances = structure.getAllowances() != null ? structure.getAllowances() : BigDecimal.ZERO;

        BigDecimal otherEarnings = (priorRecord != null && priorRecord.getOtherEarnings() != null)
                ? priorRecord.getOtherEarnings() : BigDecimal.ZERO;

        BigDecimal totalBaseSalaryResolved = basicSalary.add(hra).add(allowances);
        BigDecimal dailyRateResolved = totalBaseSalaryResolved.divide(BigDecimal.valueOf(workingDaysInMonth), 2, RoundingMode.HALF_UP);
        BigDecimal computedLopAmountResolved = dailyRateResolved.multiply(BigDecimal.valueOf(lopDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hourlyRateResolved = dailyRateResolved.divide(BigDecimal.valueOf(expectedHoursPerDay), 2, RoundingMode.HALF_UP);
        BigDecimal computedOvertimeAmountResolved = hourlyRateResolved.multiply(BigDecimal.valueOf(totalOvertimeHours * otMultiplier)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal overtimeAmount = (computedOvertimeAmountResolved.compareTo(BigDecimal.ZERO) > 0)
                ? computedOvertimeAmountResolved
                : (priorRecord != null && priorRecord.getOvertimeAmount() != null ? priorRecord.getOvertimeAmount() : BigDecimal.ZERO);

        BigDecimal totalBonus = (computedBonus.compareTo(BigDecimal.ZERO) > 0)
                ? computedBonus
                : (priorRecord != null && priorRecord.getBonusAmount() != null ? priorRecord.getBonusAmount() : BigDecimal.ZERO);

        BigDecimal totalYearlyBonus = (computedYearlyBonus.compareTo(BigDecimal.ZERO) > 0)
                ? computedYearlyBonus
                : (priorRecord != null && priorRecord.getYearlyBonusAmount() != null ? priorRecord.getYearlyBonusAmount() : BigDecimal.ZERO);

        BigDecimal pfAmount = structure.getPfDeduction() != null ? structure.getPfDeduction() : BigDecimal.ZERO;
        BigDecimal profTax = structure.getProfessionalTax() != null ? structure.getProfessionalTax() : BigDecimal.ZERO;
        BigDecimal incomeTax = structure.getIncomeTax() != null ? structure.getIncomeTax() : BigDecimal.ZERO;

        BigDecimal lopAmount = (computedLopAmountResolved.compareTo(BigDecimal.ZERO) > 0)
                ? computedLopAmountResolved
                : (priorRecord != null && priorRecord.getLopAmount() != null ? priorRecord.getLopAmount() : BigDecimal.ZERO);

        BigDecimal otherDeductions = (priorRecord != null && priorRecord.getOtherDeductions() != null)
                ? priorRecord.getOtherDeductions() : BigDecimal.ZERO;

        EmployeeAdvanceAccount advanceAccount = getOrCreateAdvanceAccount(employeeId);
        BigDecimal outstandingBalance = advanceAccount.getOutstandingBalance() != null ? advanceAccount.getOutstandingBalance() : BigDecimal.ZERO;

        BigDecimal advanceDeduction = BigDecimal.ZERO;
        if (outstandingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            advanceDeduction = BigDecimal.ZERO;
        } else if (priorRecord != null && priorRecord.getAdvanceDeductionAmount() != null) {
            advanceDeduction = priorRecord.getAdvanceDeductionAmount().min(outstandingBalance);
        }

        BigDecimal totalEarnings = basicSalary.add(hra).add(allowances).add(overtimeAmount).add(totalBonus).add(totalYearlyBonus).add(otherEarnings);
        BigDecimal totalDeductions = pfAmount.add(profTax).add(incomeTax).add(lopAmount).add(advanceDeduction).add(otherDeductions);
        BigDecimal grossSalary = totalEarnings;
        BigDecimal netSalary = totalEarnings.subtract(totalDeductions).max(BigDecimal.ZERO);

        return PayrollRecord.builder()
                .employeeId(employeeId)
                .employeeCode(employee.getEmployeeCode())
                .employeeName(employee.getEmployeeName())
                .department(employee.getCategory())
                .designation(employee.getDesignation())
                .joiningDate(employee.getDateOfJoining())
                .payrollMonth(payrollMonth)
                .payslipNumber(payslipNo)
                .totalWorkingDays(workingDaysInMonth)
                .presentDays(presentDays)
                .leaveDays(leaveDays)
                .lopDays(lopDays)
                .overtimeHours(totalOvertimeHours)
                .basicSalary(basicSalary)
                .hra(hra)
                .allowances(allowances)
                .overtimeAmount(overtimeAmount)
                .bonusAmount(totalBonus)
                .yearlyBonusAmount(totalYearlyBonus)
                .otherEarnings(otherEarnings)
                .pfAmount(pfAmount)
                .professionalTax(profTax)
                .taxAmount(incomeTax)
                .lopAmount(lopAmount)
                .advanceDeductionAmount(advanceDeduction)
                .otherDeductions(otherDeductions)
                .grossSalary(grossSalary)
                .totalEarnings(totalEarnings)
                .totalDeductions(totalDeductions)
                .netSalary(netSalary)
                .bankName(employee.getBankName())
                .accountHolderName(employee.getAccountHolderName())
                .accountNumber(employee.getAccountNumber())
                .ifscCode(employee.getIfscCode())
                .bankBranch(employee.getBankBranch())
                .advanceId(advanceAccount.getId())
                .originalAdvanceAmount(advanceAccount.getTotalAdvanceGiven())
                .previousAdvanceBalance(outstandingBalance)
                .remainingAdvanceBalance(outstandingBalance)
                .status(PayrollRecord.PayrollStatus.DRAFT)
                .isLocked(false)
                .build();
    }

    public List<PayrollRecord> getPayrollsForMonth(String payrollMonth) {
        List<PayrollRecord> existing = payrollRepository.findByPayrollMonth(payrollMonth);
        return existing.stream()
                .filter(p -> p.getEmployeeId() != null)
                .collect(java.util.stream.Collectors.toMap(PayrollRecord::getEmployeeId, p -> p, (p1, p2) -> p1))
                .values()
                .stream()
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void deletePayslip(String recordId, String adminUsername, String reason) {
        Optional<PayrollRecord> recordOpt = payrollRepository.findById(recordId)
                .or(() -> payrollRepository.findByPayslipNumber(recordId));

        if (recordOpt.isEmpty()) {
            List<PayrollRecord> matches = payrollRepository.findAll().stream()
                    .filter(r -> recordId.equalsIgnoreCase(r.getId())
                            || recordId.equalsIgnoreCase(r.getPayslipNumber())
                            || recordId.equalsIgnoreCase(r.getEmployeeId())
                            || recordId.equalsIgnoreCase(r.getEmployeeCode()))
                    .toList();
            if (!matches.isEmpty()) {
                for (PayrollRecord r : matches) {
                    payrollRepository.delete(r);
                }
            }
            log.info("Delete requested for payslip ID/number {}, handled cleanly.", recordId);
            return;
        }

        PayrollRecord record = recordOpt.get();

        if (Boolean.TRUE.equals(record.getIsLocked())) {
            throw new IllegalStateException("Cannot delete a locked payslip. Unlock the record first.");
        }

        // Reverse advance deduction transactions if applied in this payslip
        List<AdvanceTransaction> txsToDelete = new ArrayList<>();
        if (record.getPayslipNumber() != null && !record.getPayslipNumber().isBlank()) {
            txsToDelete.addAll(advanceTransactionRepository.findAllByPayslipIdAndTransactionType(record.getPayslipNumber(), AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION));
        }
        if (record.getId() != null && !record.getId().isBlank()) {
            List<AdvanceTransaction> txsById = advanceTransactionRepository.findAllByPayslipIdAndTransactionType(record.getId(), AdvanceTransaction.TransactionType.PAYROLL_DEDUCTION);
            for (AdvanceTransaction tx : txsById) {
                if (txsToDelete.stream().noneMatch(t -> t.getId().equals(tx.getId()))) {
                    txsToDelete.add(tx);
                }
            }
        }

        for (AdvanceTransaction tx : txsToDelete) {
            try {
                deleteAdvanceTransaction(tx.getId(), adminUsername);
            } catch (Exception e) {
                log.warn("Could not reverse advance transaction {} during payslip delete: {}", tx.getId(), e.getMessage());
            }
        }

        if (record.getEmployeeId() != null && !record.getEmployeeId().isBlank()) {
            try {
                recalculateAdvanceAccountLedger(record.getEmployeeId());
            } catch (Exception e) {
                log.warn("Could not recalculate advance ledger for employee {} during payslip delete: {}", record.getEmployeeId(), e.getMessage());
            }
        }

        String payslipIdentifier = record.getPayslipNumber() != null ? record.getPayslipNumber() : record.getId();
        String empName = record.getEmployeeName() != null ? record.getEmployeeName() : "Unknown Employee";
        String monthStr = record.getPayrollMonth() != null ? record.getPayrollMonth() : "N/A";

        auditService.log("DELETE_PAYSLIP", "PAYROLL_PAYSLIPS", record.getId(),
                "Deleted payslip " + payslipIdentifier + " for employee " + empName + " (" + monthStr + ") by " + adminUsername + ". Reason: " + (reason != null ? reason : "N/A"));

        payrollRepository.delete(record);
    }

    public List<EmployeeAdvanceAccount> getAllAdvanceAccounts() {
        List<Employee> employees = employeeRepository.findAll();
        List<EmployeeAdvanceAccount> accounts = new ArrayList<>();
        for (Employee emp : employees) {
            try {
                accounts.add(getAdvanceAccount(emp.getId()));
            } catch (Exception e) {
                log.error("Failed to load advance account for employee {}: {}", emp.getId(), e.getMessage());
            }
        }
        return accounts;
    }
}

package com.app.billing.controller;

import com.app.billing.model.AdvanceTransaction;
import com.app.billing.model.EmployeeAdvanceAccount;
import com.app.billing.model.EmployeeBonus;
import com.app.billing.model.PayrollRecord;
import com.app.billing.model.SalaryStructure;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AttendanceReportService;
import com.app.billing.service.PayrollCalculationService;
import com.app.billing.service.PayslipPdfService;
import com.app.billing.service.PermissionEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
@Tag(name = "Payroll & Payslips", description = "Salary structure, employee advance accounts, monthly payroll and payslip PDF generation")
public class PayrollController {

    private final PayrollCalculationService payrollService;
    private final PayslipPdfService payslipPdfService;
    private final AttendanceReportService reportService;
    private final PermissionEvaluatorService permissionEvaluatorService;

    // --- SALARY STRUCTURE ---
    @GetMapping("/salary-structure/{employeeId}")
    @Operation(summary = "Get Salary Structure", description = "Get employee salary structure")
    public ResponseEntity<SalaryStructure> getSalaryStructure(@PathVariable String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        if (!permissionEvaluatorService.isCurrentUserAdmin() && !current.getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only view your own salary structure");
        }
        return ResponseEntity.ok(payrollService.getSalaryStructure(employeeId));
    }

    @PostMapping("/salary-structure")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "MANAGE_SALARY_STRUCTURE")
    @Operation(summary = "Save Salary Structure", description = "Save or update employee salary structure")
    public ResponseEntity<SalaryStructure> saveSalaryStructure(@RequestBody SalaryStructure structure) {
        return ResponseEntity.ok(payrollService.saveSalaryStructure(structure));
    }

    // --- SINGLE EMPLOYEE ADVANCE ACCOUNT & TRANSACTION LEDGER ---
    @GetMapping("/advance-account/all")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = Modules.VIEW)
    @Operation(summary = "Get All Advance Accounts", description = "Get list of advance accounts for all active employees")
    public ResponseEntity<List<EmployeeAdvanceAccount>> getAllAdvanceAccounts() {
        return ResponseEntity.ok(payrollService.getAllAdvanceAccounts());
    }

    @GetMapping("/advance-account/{employeeId}")
    @Operation(summary = "Get Employee Advance Account", description = "Get single advance account balance and summary for an employee")
    public ResponseEntity<EmployeeAdvanceAccount> getAdvanceAccount(@PathVariable String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        if (!permissionEvaluatorService.isCurrentUserAdmin() && !current.getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only view your own advance account");
        }
        return ResponseEntity.ok(payrollService.getAdvanceAccount(employeeId));
    }

    @GetMapping("/advance-account/{employeeId}/transactions")
    @Operation(summary = "Get Advance Transactions", description = "Get full transaction ledger for an employee's advance account")
    public ResponseEntity<List<AdvanceTransaction>> getAdvanceTransactions(@PathVariable String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        if (!permissionEvaluatorService.isCurrentUserAdmin() && !current.getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only view your own advance transactions");
        }
        return ResponseEntity.ok(payrollService.getAdvanceTransactions(employeeId));
    }

    @GetMapping("/check-advance/{employeeId}")
    @Operation(summary = "Check Outstanding Advance", description = "Check outstanding advance balance for active advance detection before payslip generation")
    public ResponseEntity<BigDecimal> checkOutstandingAdvance(@PathVariable String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        if (!permissionEvaluatorService.isCurrentUserAdmin() && !current.getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only check your own advance balance");
        }
        return ResponseEntity.ok(payrollService.calculateOutstandingBalance(employeeId));
    }

    @PostMapping("/advance-account/{employeeId}/advance")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Add Salary Advance (Admin)", description = "Record a new salary advance entry to an employee's single advance account")
    public ResponseEntity<AdvanceTransaction> addSalaryAdvance(
            @PathVariable String employeeId,
            @RequestBody AdvanceTransaction transaction) {
        var current = permissionEvaluatorService.currentEmployee();
        String operatorUsername = current != null ? current.getEmail() : "Admin";
        transaction.setTransactionType(AdvanceTransaction.TransactionType.ADVANCE_GIVEN);
        return ResponseEntity.ok(payrollService.addAdvanceTransaction(employeeId, transaction, operatorUsername));
    }

    @PostMapping("/advance-account/{employeeId}/repayment")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Record Advance Repayment (Admin)", description = "Record a direct repayment entry to an employee's single advance account")
    public ResponseEntity<AdvanceTransaction> recordRepayment(
            @PathVariable String employeeId,
            @RequestBody AdvanceTransaction transaction) {
        var current = permissionEvaluatorService.currentEmployee();
        String operatorUsername = current != null ? current.getEmail() : "Admin";
        transaction.setTransactionType(AdvanceTransaction.TransactionType.REPAYMENT);
        return ResponseEntity.ok(payrollService.addAdvanceTransaction(employeeId, transaction, operatorUsername));
    }

    @PostMapping("/advance-account/{employeeId}/transaction")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Add Advance Transaction (Admin)", description = "Record advance given, repayment, deduction, or adjustment for an employee")
    public ResponseEntity<AdvanceTransaction> addAdvanceTransaction(
            @PathVariable String employeeId,
            @RequestBody AdvanceTransaction transaction) {
        var current = permissionEvaluatorService.currentEmployee();
        String operatorUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.addAdvanceTransaction(employeeId, transaction, operatorUsername));
    }

    @PutMapping("/advance-transaction/{transactionId}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Update Advance Transaction", description = "Update payment mode, reference number, or details of an advance transaction")
    public ResponseEntity<AdvanceTransaction> updateAdvanceTransaction(
            @PathVariable String transactionId,
            @RequestBody AdvanceTransaction transaction) {
        var current = permissionEvaluatorService.currentEmployee();
        String operatorUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.updateAdvanceTransaction(transactionId, transaction, operatorUsername));
    }

    @DeleteMapping("/advance-transaction/{transactionId}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Void/Delete Advance Transaction", description = "Void an advance transaction and recalculate account balance")
    public ResponseEntity<Void> deleteAdvanceTransaction(@PathVariable String transactionId) {
        var current = permissionEvaluatorService.currentEmployee();
        String operatorUsername = current != null ? current.getEmail() : "Admin";
        payrollService.deleteAdvanceTransaction(transactionId, operatorUsername);
        return ResponseEntity.ok().build();
    }

    // --- PAYROLL PROCESSING & PAYSLIP GENERATION ---
    @PostMapping("/process/{payrollMonth}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Process Monthly Payroll", description = "Process monthly payroll for all active employees")
    public ResponseEntity<List<PayrollRecord>> processPayroll(@PathVariable String payrollMonth) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.processPayrollForAll(payrollMonth, adminUsername));
    }

    @PostMapping("/batch-process")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Batch Process Monthly Payroll", description = "Batch process payroll for all employees with custom advance deductions")
    public ResponseEntity<List<PayrollRecord>> batchProcessPayroll(@RequestBody BatchProcessRequest request) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.batchProcessPayroll(request.getPayrollMonth(), request.getEmployeeDeductionsMap(), adminUsername));
    }

    @PostMapping("/process-single/{employeeId}/{payrollMonth}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Process Payroll for Employee", description = "Generate and finalize payslip for a specific employee with optional advance deduction")
    public ResponseEntity<PayrollRecord> processPayrollForEmployee(
            @PathVariable String employeeId,
            @PathVariable String payrollMonth,
            @RequestParam(required = false) BigDecimal advanceDeductionAmount) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.processPayrollForEmployee(employeeId, payrollMonth, advanceDeductionAmount, adminUsername));
    }

    @PostMapping("/save-draft")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Save Draft Payroll", description = "Save draft payroll entries and adjustments for an employee")
    public ResponseEntity<PayrollRecord> saveDraftPayroll(@RequestBody PayrollRecord draft) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.saveDraftPayroll(draft, adminUsername));
    }

    @PutMapping("/lock/{id}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Lock Payroll Record", description = "Lock a payroll record to prevent unauthorized edits")
    public ResponseEntity<PayrollRecord> lockPayroll(@PathVariable String id) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.lockPayroll(id, adminUsername));
    }

    @PutMapping("/unlock/{id}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = "PROCESS_PAYROLL")
    @Operation(summary = "Unlock Payroll Record", description = "Unlock a locked payroll record with mandatory audit reason")
    public ResponseEntity<PayrollRecord> unlockPayroll(@PathVariable String id, @RequestParam(required = false) String reason) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(payrollService.unlockPayroll(id, adminUsername, reason));
    }

    @GetMapping("/payslip/my-payslips")
    @Operation(summary = "My Payslips", description = "Get payslips for logged-in employee")
    public ResponseEntity<List<PayrollRecord>> getMyPayslips() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        return ResponseEntity.ok(payrollService.getPayrollsForEmployee(current.getId()));
    }

    @GetMapping("/payslip/month/{payrollMonth}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = Modules.VIEW)
    @Operation(summary = "Payslips by Month", description = "Get all payslips for a given month (Admin sees all, Employee sees own)")
    public ResponseEntity<List<PayrollRecord>> getPayslipsByMonth(
            @PathVariable String payrollMonth,
            @RequestParam(required = false) String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        List<PayrollRecord> records = payrollService.getPayrollsForMonth(payrollMonth);
        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        String targetId = !isAdmin && current != null ? current.getId() : employeeId;

        if (targetId != null && !targetId.trim().isEmpty()) {
            records = records.stream()
                    .filter(r -> targetId.equalsIgnoreCase(r.getEmployeeId()))
                    .collect(java.util.stream.Collectors.toList());
        }
        return ResponseEntity.ok(records);
    }

    @DeleteMapping("/payslip/{id}")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = Modules.DELETE)
    @Operation(summary = "Delete Payslip", description = "Admin delete a generated payslip with audit record")
    public ResponseEntity<Void> deletePayslip(
            @PathVariable String id,
            @RequestParam(required = false) String reason) {
        var current = permissionEvaluatorService.currentEmployee();
        String operator = current != null ? (current.getEmail() != null && !current.getEmail().isBlank() ? current.getEmail() : (current.getEmployeeCode() != null ? current.getEmployeeCode() : current.getId())) : "Admin";
        payrollService.deletePayslip(id, operator, reason);
        return ResponseEntity.ok().build();
    }

    @GetMapping({"/payslip/id/{id}/pdf", "/payslip/record/{id}/pdf"})
    @Operation(summary = "Download Payslip PDF by Record ID", description = "Download official PDF payslip by record ID")
    public ResponseEntity<byte[]> downloadPayslipPdfById(@PathVariable String id) {
        PayrollRecord record = payrollService.getPayrollRecordById(id);
        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        var current = permissionEvaluatorService.currentEmployee();

        if (!isAdmin) {
            if (current == null) {
                throw new AccessDeniedException("User not authenticated");
            }
            boolean isOwn = current.getId().equals(record.getEmployeeId())
                    || (current.getEmployeeCode() != null && current.getEmployeeCode().equalsIgnoreCase(record.getEmployeeCode()))
                    || (current.getEmployeeCode() != null && current.getEmployeeCode().equalsIgnoreCase(record.getEmployeeId()));
            if (!isOwn) {
                throw new AccessDeniedException("You can only download your own payslips");
            }
        }

        byte[] pdf = payslipPdfService.generatePayslipPdf(record);
        String payslipNo = record.getPayslipNumber() != null && !record.getPayslipNumber().isBlank() ? record.getPayslipNumber() : record.getId();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Payslip_" + payslipNo + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/payslip/{employeeId}/{payrollMonth}/pdf")
    @Operation(summary = "Download Payslip PDF", description = "Download official PDF payslip")
    public ResponseEntity<byte[]> downloadPayslipPdf(
            @PathVariable String employeeId,
            @PathVariable String payrollMonth) {
        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        var current = permissionEvaluatorService.currentEmployee();

        PayrollRecord record = payrollService.getPayrollRecord(employeeId, payrollMonth);

        if (!isAdmin) {
            if (current == null) {
                throw new AccessDeniedException("User not authenticated");
            }
            boolean isOwn = current.getId().equals(record.getEmployeeId())
                    || current.getId().equals(employeeId)
                    || (current.getEmployeeCode() != null && (current.getEmployeeCode().equalsIgnoreCase(record.getEmployeeCode())
                    || current.getEmployeeCode().equalsIgnoreCase(record.getEmployeeId())
                    || current.getEmployeeCode().equalsIgnoreCase(employeeId)));
            if (!isOwn) {
                throw new AccessDeniedException("You can only download your own payslips");
            }
        }

        byte[] pdf = payslipPdfService.generatePayslipPdf(record);
        String payslipNo = record.getPayslipNumber() != null && !record.getPayslipNumber().isBlank() ? record.getPayslipNumber() : record.getId();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Payslip_" + payslipNo + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // --- BONUSES ---
    @PostMapping("/bonus")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = Modules.CREATE)
    @Operation(summary = "Add Employee Bonus", description = "Add performance, festival, or yearly bonus for an employee")
    public ResponseEntity<EmployeeBonus> addBonus(@RequestBody EmployeeBonus bonus) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? (current.getEmail() != null && !current.getEmail().isBlank() ? current.getEmail() : (current.getEmployeeCode() != null ? current.getEmployeeCode() : current.getId())) : "Admin";
        return ResponseEntity.ok(payrollService.addBonus(bonus, adminUsername));
    }

    @GetMapping("/bonus/my-bonuses")
    @Operation(summary = "My Bonuses", description = "Get bonuses for logged-in employee")
    public ResponseEntity<List<EmployeeBonus>> getMyBonuses() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(payrollService.getBonusesForEmployee(current.getId()));
    }

    @GetMapping("/bonus/all")
    @RequiresPermission(module = Modules.PAYROLL_PAYSLIPS, action = Modules.VIEW)
    @Operation(summary = "Get All Bonuses", description = "Get all employee bonuses, optionally filtered by employee ID")
    public ResponseEntity<List<EmployeeBonus>> getAllBonuses(@RequestParam(required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank()) {
            return ResponseEntity.ok(payrollService.getBonusesForEmployee(employeeId));
        }
        return ResponseEntity.ok(payrollService.getAllBonuses());
    }

    @GetMapping("/export/excel")
    @RequiresPermission(module = Modules.PAYROLL, action = Modules.EXPORT)
    @Operation(summary = "Export Payroll Excel Report", description = "Export monthly payroll report to Excel")
    public ResponseEntity<byte[]> exportPayrollExcel(@RequestParam(required = false) String payrollMonth) throws IOException {
        byte[] excel = reportService.generatePayrollReportExcel(payrollMonth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Payroll_Report.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/export/pdf")
    @RequiresPermission(module = Modules.PAYROLL, action = Modules.EXPORT)
    @Operation(summary = "Export Payroll PDF Report", description = "Export monthly payroll report to PDF")
    public ResponseEntity<byte[]> exportPayrollPdf(@RequestParam(required = false) String payrollMonth) {
        byte[] pdf = reportService.generatePayrollReportPdf(payrollMonth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Payroll_Report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/advance/export/excel")
    @RequiresPermission(module = Modules.SALARY_ADVANCE, action = Modules.EXPORT)
    @Operation(summary = "Export Salary Advances Excel")
    public ResponseEntity<byte[]> exportAdvanceExcel(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status) throws IOException {
        byte[] excel = reportService.generateSalaryAdvanceReportExcel(employeeId, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Salary_Advances.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/advance/export/pdf")
    @RequiresPermission(module = Modules.SALARY_ADVANCE, action = Modules.EXPORT)
    @Operation(summary = "Export Salary Advances PDF")
    public ResponseEntity<byte[]> exportAdvancePdf(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status) {
        byte[] pdf = reportService.generateSalaryAdvanceReportPdf(employeeId, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Salary_Advances.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Data
    public static class BatchProcessRequest {
        private String payrollMonth;
        private java.util.Map<String, BigDecimal> employeeDeductionsMap;
    }
}

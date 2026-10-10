package com.app.billing.controller;

import com.app.billing.dto.EmployeeDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@Tag(name = "Employee Management", description = "Employee CRUD operations")
public class EmployeeController {
    
    private final EmployeeService employeeService;
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create employee", description = "Create a new employee (Admin only)")
    public ResponseEntity<EmployeeDto> create(@Valid @RequestBody EmployeeDto dto) {
        return new ResponseEntity<>(employeeService.create(dto), HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update employee", description = "Update existing employee (Admin only)")
    public ResponseEntity<EmployeeDto> update(@PathVariable String id, @Valid @RequestBody EmployeeDto dto) {
        return ResponseEntity.ok(employeeService.update(id, dto));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID", description = "Retrieve employee by ID")
    public ResponseEntity<EmployeeDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(employeeService.findById(id));
    }
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all employees", description = "Retrieve all employees with pagination (Admin only)")
    public ResponseEntity<PageResponse<EmployeeDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(employeeService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/categories")
    @Operation(summary = "Get employee categories", description = "Get list of all employee categories from Master")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(employeeService.getCategories());
    }

    @GetMapping("/list")
    @Operation(summary = "Get employee dropdown list", description = "Get list of employees for selectors based on user authorization")
    public ResponseEntity<List<EmployeeDto>> getEmployeeList() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }

        boolean canViewAll = permissionEvaluatorService.isCurrentUserAdmin()
                || permissionEvaluatorService.hasPermission(com.app.billing.security.Modules.MASTER_ATTENDANCE, "view")
                || permissionEvaluatorService.hasPermission(com.app.billing.security.Modules.LEAVES, "view")
                || permissionEvaluatorService.hasPermission(com.app.billing.security.Modules.PERMISSIONS, "view")
                || permissionEvaluatorService.hasPermission(com.app.billing.security.Modules.PAYROLL_PAYSLIPS, "view")
                || permissionEvaluatorService.hasPermission(com.app.billing.security.Modules.EMPLOYEES, "view");

        if (canViewAll) {
            return ResponseEntity.ok(employeeService.findAllForExport());
        } else {
            EmployeeDto selfDto = employeeService.findById(current.getId());
            return ResponseEntity.ok(List.of(selfDto));
        }
    }
    
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search employees", description = "Search employees by code, name, email, phone, category, or designation (Admin only)")
    public ResponseEntity<PageResponse<EmployeeDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(employeeService.search(searchTerm, page, size, sortBy, sortDir));
    }
    
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export employees", description = "Export all employees for Excel (Admin only)")
    public ResponseEntity<List<EmployeeDto>> exportEmployees() {
        return ResponseEntity.ok(employeeService.findAllForExport());
    }
    
    private final com.app.billing.service.PermissionEvaluatorService permissionEvaluatorService;

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete employee", description = "Delete employee (Admin only)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- MULTIPLE BANK ACCOUNTS ENDPOINTS ---

    @GetMapping("/{id}/bank-accounts")
    @Operation(summary = "Get employee bank accounts", description = "Get bank accounts for employee (Owner or Admin)")
    public ResponseEntity<List<com.app.billing.model.EmployeeBankAccount>> getBankAccounts(@PathVariable String id) {
        validateOwnerOrAdmin(id);
        return ResponseEntity.ok(employeeService.getBankAccounts(id));
    }

    @PostMapping("/{id}/bank-accounts")
    @Operation(summary = "Add bank account", description = "Add bank account for employee (Owner or Admin)")
    public ResponseEntity<List<com.app.billing.model.EmployeeBankAccount>> addBankAccount(
            @PathVariable String id,
            @RequestBody com.app.billing.model.EmployeeBankAccount account) {
        validateOwnerOrAdmin(id);
        return ResponseEntity.ok(employeeService.addBankAccount(id, account));
    }

    @PutMapping("/{id}/bank-accounts/{accountId}")
    @Operation(summary = "Update bank account", description = "Update bank account for employee (Owner or Admin)")
    public ResponseEntity<List<com.app.billing.model.EmployeeBankAccount>> updateBankAccount(
            @PathVariable String id,
            @PathVariable String accountId,
            @RequestBody com.app.billing.model.EmployeeBankAccount account) {
        validateOwnerOrAdmin(id);
        return ResponseEntity.ok(employeeService.updateBankAccount(id, accountId, account));
    }

    @DeleteMapping("/{id}/bank-accounts/{accountId}")
    @Operation(summary = "Delete bank account", description = "Delete bank account for employee (Owner or Admin)")
    public ResponseEntity<List<com.app.billing.model.EmployeeBankAccount>> deleteBankAccount(
            @PathVariable String id,
            @PathVariable String accountId) {
        validateOwnerOrAdmin(id);
        return ResponseEntity.ok(employeeService.deleteBankAccount(id, accountId));
    }

    @PutMapping("/{id}/bank-accounts/{accountId}/set-primary")
    @Operation(summary = "Set primary bank account", description = "Set primary bank account for employee (Owner or Admin)")
    public ResponseEntity<List<com.app.billing.model.EmployeeBankAccount>> setPrimaryBankAccount(
            @PathVariable String id,
            @PathVariable String accountId) {
        validateOwnerOrAdmin(id);
        return ResponseEntity.ok(employeeService.setPrimaryBankAccount(id, accountId));
    }

    private void validateOwnerOrAdmin(String targetEmployeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        if (!permissionEvaluatorService.isCurrentUserAdmin() && !current.getId().equals(targetEmployeeId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view/manage bank accounts for this employee");
        }
    }
}


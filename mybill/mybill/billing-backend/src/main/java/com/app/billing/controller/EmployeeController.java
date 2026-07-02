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
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete employee", description = "Delete employee (Admin only)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}


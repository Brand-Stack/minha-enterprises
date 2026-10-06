package com.app.billing.controller;

import com.app.billing.dto.EmployeeCategoryDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.service.EmployeeCategoryService;
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
@RequestMapping("/employee-categories")
@RequiredArgsConstructor
@Tag(name = "Employee Categories", description = "Employee category / role management (Admin only)")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeCategoryController {

    private final EmployeeCategoryService employeeCategoryService;

    @PostMapping
    @Operation(summary = "Create category")
    public ResponseEntity<EmployeeCategoryDto> create(@Valid @RequestBody EmployeeCategoryDto dto) {
        return new ResponseEntity<>(employeeCategoryService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category")
    public ResponseEntity<EmployeeCategoryDto> update(@PathVariable String id,
                                                      @Valid @RequestBody EmployeeCategoryDto dto) {
        return ResponseEntity.ok(employeeCategoryService.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by id")
    public ResponseEntity<EmployeeCategoryDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(employeeCategoryService.findById(id));
    }

    @GetMapping
    @Operation(summary = "List categories (paged)")
    public ResponseEntity<PageResponse<EmployeeCategoryDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(employeeCategoryService.findAll(page, size, sortBy, sortDir));
    }

    @GetMapping("/list")
    @Operation(summary = "List all categories (no pagination, for dropdowns)")
    public ResponseEntity<List<EmployeeCategoryDto>> findAllList() {
        return ResponseEntity.ok(employeeCategoryService.findAllList());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        employeeCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.app.billing.controller;

import com.app.billing.dto.ExpenseDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cash-in")
@RequiredArgsConstructor
@Tag(name = "Cash In Management", description = "Cash In operations")
public class ExpenseController {
    
    private final ExpenseService expenseService;
    
    @PostMapping
    @RequiresPermission(module = Modules.EXPENSES, action = Modules.CREATE)
    @Operation(summary = "Create expense", description = "Create a new expense record")
    public ResponseEntity<ExpenseDto> create(@Valid @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(expenseService.create(dto));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get expense by ID", description = "Retrieve expense by ID")
    public ResponseEntity<ExpenseDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(expenseService.findById(id));
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.EXPENSES, action = Modules.EDIT)
    @Operation(summary = "Update expense", description = "Update existing expense")
    public ResponseEntity<ExpenseDto> update(@PathVariable String id, @Valid @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(expenseService.update(id, dto));
    }
    
    @GetMapping
    @RequiresPermission(module = Modules.EXPENSES, action = Modules.VIEW)
    @Operation(summary = "Get all expenses", description = "Retrieve all expenses with pagination")
    public ResponseEntity<PageResponse<ExpenseDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expenseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(expenseService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/filter")
    @Operation(summary = "Filter expenses by date range", description = "Filter expenses by date range")
    public ResponseEntity<PageResponse<ExpenseDto>> findByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(expenseService.findByDateRange(startDate, endDate, page, size));
    }
    
    @GetMapping("/category/{category}")
    @Operation(summary = "Get expenses by category", description = "Retrieve expenses by category")
    public ResponseEntity<List<ExpenseDto>> findByCategory(@PathVariable String category) {
        return ResponseEntity.ok(expenseService.findByCategory(category));
    }
    
    @GetMapping("/category/{category}/filter")
    @Operation(summary = "Filter expenses by category and date range", description = "Filter expenses by category and date range")
    public ResponseEntity<PageResponse<ExpenseDto>> findByCategoryAndDateRange(
            @PathVariable String category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(expenseService.findByCategoryAndDateRange(category, startDate, endDate, page, size));
    }
    
    @GetMapping("/categories")
    @Operation(summary = "Get default categories", description = "Retrieve list of default expense categories")
    public ResponseEntity<List<String>> getDefaultCategories() {
        return ResponseEntity.ok(expenseService.getDefaultCategories());
    }
    
    @GetMapping("/categories/totals")
    @Operation(summary = "Get category totals", description = "Retrieve total amounts by category")
    public ResponseEntity<Map<String, BigDecimal>> getCategoryTotals() {
        return ResponseEntity.ok(expenseService.getCategoryTotals());
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.EXPENSES, action = Modules.DELETE)
    @Operation(summary = "Delete expense", description = "Delete expense by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}


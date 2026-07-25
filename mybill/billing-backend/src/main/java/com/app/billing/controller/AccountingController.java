package com.app.billing.controller;

import com.app.billing.dto.AccountingEntryDto;
import com.app.billing.dto.AccountingSummaryDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AccountingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/accounting")
@RequiredArgsConstructor
@Tag(name = "Accounting", description = "Cash IN / OUT with running balance")
public class AccountingController {

    private final AccountingService accountingService;

    @PostMapping("/entries")
    @RequiresPermission(module = Modules.ACCOUNTING, action = Modules.CREATE)
    @Operation(summary = "Create IN or OUT entry")
    public ResponseEntity<AccountingEntryDto> create(@Valid @RequestBody AccountingEntryDto dto) {
        return new ResponseEntity<>(accountingService.create(dto), HttpStatus.CREATED);
    }

    @GetMapping("/entries")
    @RequiresPermission(module = Modules.ACCOUNTING, action = Modules.VIEW)
    @Operation(summary = "List entries with optional filters (full list; use for export)")
    public ResponseEntity<List<AccountingEntryDto>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer calendarMonth,
            @RequestParam(required = false) Integer calendarYear) {
        return ResponseEntity.ok(accountingService.search(dateFrom, dateTo, type, calendarMonth, calendarYear));
    }

    @GetMapping("/entries/paged")
    @RequiresPermission(module = Modules.ACCOUNTING, action = Modules.VIEW)
    @Operation(summary = "Paged list with optional filters and sort")
    public ResponseEntity<PageResponse<AccountingEntryDto>> searchPaged(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer calendarMonth,
            @RequestParam(required = false) Integer calendarYear,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "entryDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(accountingService.searchPaged(dateFrom, dateTo, type, calendarMonth, calendarYear,
                page, size, sortBy, sortDir));
    }

    @GetMapping("/summary")
    @Operation(summary = "Dashboard-style totals")
    public ResponseEntity<AccountingSummaryDto> summary() {
        return ResponseEntity.ok(accountingService.summary());
    }

    @PutMapping("/entries/{id}")
    @RequiresPermission(module = Modules.ACCOUNTING, action = Modules.EDIT)
    @Operation(summary = "Update entry and recompute running balances")
    public ResponseEntity<AccountingEntryDto> update(@PathVariable String id, @Valid @RequestBody AccountingEntryDto dto) {
        return ResponseEntity.ok(accountingService.update(id, dto));
    }

    @GetMapping("/report-totals")
    @Operation(summary = "Get totals for filtered accounting entries")
    public ResponseEntity<java.util.Map<String, Object>> reportTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer calendarMonth,
            @RequestParam(required = false) Integer calendarYear) {
        java.math.BigDecimal total = accountingService.getTotalAmountForFilters(dateFrom, dateTo, type, calendarMonth, calendarYear);
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("totalAmount", total);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/entries/{id}")
    @RequiresPermission(module = Modules.ACCOUNTING, action = Modules.DELETE)
    @Operation(summary = "Delete entry and recompute running balances")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        accountingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

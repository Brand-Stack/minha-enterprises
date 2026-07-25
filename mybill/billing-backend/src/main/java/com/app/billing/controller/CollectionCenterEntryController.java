package com.app.billing.controller;

import com.app.billing.dto.CollectionCenterEntryDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.dto.CollectionCustomerAwbDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CollectionCenterEntryService;
import com.app.billing.service.CollectionCustomerAwbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/collection-center")
@RequiredArgsConstructor
@Tag(name = "Collection Center", description = "Per-AWB collection entries under a collection customer")
public class CollectionCenterEntryController {

    private final CollectionCenterEntryService service;
    private final CollectionCustomerAwbService collectionCustomerAwbService;

    @PostMapping("/entries")
    @RequiresPermission(module = Modules.COLLECTION_CENTER, action = Modules.CREATE)
    @Operation(summary = "Create entry")
    public ResponseEntity<CollectionCenterEntryDto> create(@Valid @RequestBody CollectionCenterEntryDto dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/entries/{id}")
    @RequiresPermission(module = Modules.COLLECTION_CENTER, action = Modules.EDIT)
    @Operation(summary = "Update entry")
    public ResponseEntity<CollectionCenterEntryDto> update(@PathVariable String id, @RequestBody CollectionCenterEntryDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @GetMapping("/entries/{id}")
    @Operation(summary = "Get entry by id")
    public ResponseEntity<CollectionCenterEntryDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/customers/{customerId}/entries")
    @Operation(summary = "List entries for a collection customer")
    public ResponseEntity<List<CollectionCenterEntryDto>> listByCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(service.findByCustomerId(customerId));
    }

    @GetMapping("/entries")
    @RequiresPermission(module = Modules.COLLECTION_CENTER, action = Modules.VIEW)
    @Operation(summary = "Search entries with filters")
    public ResponseEntity<PageResponse<CollectionCenterEntryDto>> search(
            @RequestParam(required = false) String collectionCustomerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String awbNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String amountStatus,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String entryMonth,
            @RequestParam(required = false) Integer entryYear,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "entryDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(service.search(collectionCustomerId, customerName, awbNo, dateFrom, dateTo,
                status, amountStatus, pincode, courier, entryMonth, entryYear, page, size, sortBy, sortDir));
    }

    @GetMapping("/entries/report-totals")
    @Operation(summary = "Totals for current filter (same params as search)")
    public ResponseEntity<CollectionCenterEntryService.SearchTotals> reportTotals(
            @RequestParam(required = false) String collectionCustomerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String awbNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String amountStatus,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String entryMonth,
            @RequestParam(required = false) Integer entryYear) {
        return ResponseEntity.ok(service.reportTotals(collectionCustomerId, customerName, awbNo, dateFrom, dateTo,
                status, amountStatus, pincode, courier, entryMonth, entryYear));
    }

    @GetMapping("/registry/pending")
    @Operation(summary = "Pending AWBs in registry (optional customer, date/month/year on createdAt)")
    public ResponseEntity<List<CollectionCustomerAwbDto>> pendingRegistry(
            @RequestParam(required = false) String collectionCustomerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) Integer calendarMonth,
            @RequestParam(required = false) Integer calendarYear) {
        return ResponseEntity.ok(collectionCustomerAwbService.listPendingOverall(collectionCustomerId,
                createdFrom, createdTo, calendarMonth, calendarYear));
    }

    @PatchMapping("/entries/{id}/status")
    @RequiresPermission(module = Modules.COLLECTION_CENTER, action = Modules.EDIT)
    @Operation(summary = "Patch status/remarks for collection center entry")
    public ResponseEntity<CollectionCenterEntryDto> patchStatus(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        CollectionCenterEntryDto updated = service.patchStatus(id, payload.get("amountStatus"), payload.get("description"));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/entries/{id}")
    @RequiresPermission(module = Modules.COLLECTION_CENTER, action = Modules.DELETE)
    @Operation(summary = "Delete entry")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.app.billing.controller;

import com.app.billing.dto.CashBookingDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CashBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/cash-bookings")
@RequiredArgsConstructor
@Tag(name = "Cash Booking", description = "Manual / direct courier bookings")
public class CashBookingController {

    private final CashBookingService service;

    @PostMapping
    @RequiresPermission(module = Modules.CASH_BOOKING, action = Modules.CREATE)
    @Operation(summary = "Create cash booking")
    public ResponseEntity<CashBookingDto> create(@Valid @RequestBody CashBookingDto dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.CASH_BOOKING, action = Modules.EDIT)
    @Operation(summary = "Update cash booking")
    public ResponseEntity<CashBookingDto> update(@PathVariable String id, @Valid @RequestBody CashBookingDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get by id")
    public ResponseEntity<CashBookingDto> get(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.CASH_BOOKING, action = Modules.DELETE)
    @Operation(summary = "Delete cash booking")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @RequiresPermission(module = Modules.CASH_BOOKING, action = Modules.VIEW)
    @Operation(summary = "Search with filters and pagination")
    public ResponseEntity<PageResponse<CashBookingDto>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String awbNo,
            @RequestParam(required = false) String receiverName,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String areaName,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String amountStatus,
            @RequestParam(required = false) String remarks,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "bookingDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(service.search(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, remarks, page, size, sortBy, sortDir));
    }

    @GetMapping("/report-totals")
    @Operation(summary = "Totals for current filter (report export)")
    public ResponseEntity<CashBookingService.SearchTotals> reportTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String awbNo,
            @RequestParam(required = false) String receiverName,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String areaName,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String amountStatus,
            @RequestParam(required = false) String remarks) {
        return ResponseEntity.ok(service.reportTotals(dateFrom, dateTo, awbNo, receiverName, pincode, state, areaName,
                courier, status, amountStatus, remarks));
    }
}

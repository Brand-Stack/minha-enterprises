package com.app.billing.controller;

import com.app.billing.dto.PageResponse;
import com.app.billing.dto.PurchaseBillDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.PurchaseBillService;
import com.app.billing.service.PurchaseBillWordTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/purchase-bills")
@RequiredArgsConstructor
@Tag(name = "Purchase Bill Management", description = "Purchase bill operations")
public class PurchaseBillController {
    
    private final PurchaseBillService purchaseBillService;
    private final PurchaseBillWordTemplateService purchaseBillWordTemplateService;
    
    @PostMapping
    @RequiresPermission(module = Modules.PURCHASE_BILLS, action = Modules.CREATE)
    @Operation(summary = "Create purchase bill", description = "Create a new purchase bill")
    public ResponseEntity<PurchaseBillDto> create(@Valid @RequestBody PurchaseBillDto dto) {
        return new ResponseEntity<>(purchaseBillService.create(dto), HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get purchase bill by ID", description = "Retrieve purchase bill by ID")
    public ResponseEntity<PurchaseBillDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(purchaseBillService.findById(id));
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.PURCHASE_BILLS, action = Modules.EDIT)
    @Operation(summary = "Update purchase bill", description = "Update existing purchase bill")
    public ResponseEntity<PurchaseBillDto> update(@PathVariable String id, @Valid @RequestBody PurchaseBillDto dto) {
        return ResponseEntity.ok(purchaseBillService.update(id, dto));
    }
    
    @GetMapping
    @RequiresPermission(module = Modules.PURCHASE_BILLS, action = Modules.VIEW)
    @Operation(summary = "Get all purchase bills", description = "Retrieve all purchase bills with pagination")
    public ResponseEntity<PageResponse<PurchaseBillDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        // Validate and sanitize sortBy field
        if (sortBy == null || sortBy.trim().isEmpty()) {
            sortBy = "createdAt";
        }
        // Validate sortDir
        if (sortDir == null || (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc"))) {
            sortDir = "desc";
        }
        return ResponseEntity.ok(purchaseBillService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/filter")
    @Operation(summary = "Filter purchase bills by date range", description = "Filter purchase bills by date range")
    public ResponseEntity<PageResponse<PurchaseBillDto>> findByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(purchaseBillService.findByDateRange(startDate, endDate, page, size));
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.PURCHASE_BILLS, action = Modules.DELETE)
    @Operation(summary = "Delete purchase bill", description = "Delete purchase bill by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        purchaseBillService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/print")
    @RequiresPermission(module = Modules.PURCHASE_BILLS, action = Modules.PRINT)
    @Operation(summary = "Print purchase bill as PDF", description = "Generate and return purchase bill as PDF")
    public ResponseEntity<byte[]> printPurchaseBill(@PathVariable String id) {
        PurchaseBillDto bill = purchaseBillService.findById(id);
        byte[] pdfBytes = purchaseBillWordTemplateService.generatePurchaseBillPdf(bill);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "purchase-bill-" + bill.getBillNumber() + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    
    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.PURCHASE_BILLS, action = Modules.DOWNLOAD)
    @Operation(summary = "Download purchase bill as PDF", description = "Generate and download purchase bill as PDF file")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        PurchaseBillDto bill = purchaseBillService.findById(id);
        byte[] pdfBytes = purchaseBillWordTemplateService.generatePurchaseBillPdf(bill);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "purchase-bill-" + bill.getBillNumber() + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}


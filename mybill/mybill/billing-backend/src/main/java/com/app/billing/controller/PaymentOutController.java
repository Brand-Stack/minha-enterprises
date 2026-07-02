package com.app.billing.controller;

import com.app.billing.dto.PageResponse;
import com.app.billing.dto.PaymentOutDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.PaymentOutService;
import com.app.billing.service.PaymentOutWordTemplateService;
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
@RequestMapping("/payment-out")
@RequiredArgsConstructor
@Tag(name = "Payment-Out Management", description = "Payment-Out operations")
public class PaymentOutController {
    
    private final PaymentOutService paymentOutService;
    private final PaymentOutWordTemplateService paymentOutWordTemplateService;
    
    @PostMapping
    @RequiresPermission(module = Modules.PAYMENT_OUT, action = Modules.CREATE)
    @Operation(summary = "Create payment-out", description = "Create a new payment-out record")
    public ResponseEntity<PaymentOutDto> create(@Valid @RequestBody PaymentOutDto dto) {
        return new ResponseEntity<>(paymentOutService.create(dto), HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get payment-out by ID", description = "Retrieve payment-out by ID")
    public ResponseEntity<PaymentOutDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(paymentOutService.findById(id));
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.PAYMENT_OUT, action = Modules.EDIT)
    @Operation(summary = "Update payment-out", description = "Update existing payment-out")
    public ResponseEntity<PaymentOutDto> update(@PathVariable String id, @Valid @RequestBody PaymentOutDto dto) {
        return ResponseEntity.ok(paymentOutService.update(id, dto));
    }
    
    @GetMapping
    @RequiresPermission(module = Modules.PAYMENT_OUT, action = Modules.VIEW)
    @Operation(summary = "Get all payment-out records", description = "Retrieve all payment-out records with pagination")
    public ResponseEntity<PageResponse<PaymentOutDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(paymentOutService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/filter")
    @Operation(summary = "Filter payment-out by date range", description = "Filter payment-out by date range")
    public ResponseEntity<PageResponse<PaymentOutDto>> findByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(paymentOutService.findByDateRange(startDate, endDate, page, size));
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.PAYMENT_OUT, action = Modules.DELETE)
    @Operation(summary = "Delete payment-out", description = "Delete payment-out by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        paymentOutService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/print")
    @RequiresPermission(module = Modules.PAYMENT_OUT, action = Modules.PRINT)
    @Operation(summary = "Print payment-out as PDF", description = "Generate and return payment-out as PDF")
    public ResponseEntity<byte[]> printPaymentOut(@PathVariable String id) {
        PaymentOutDto payment = paymentOutService.findById(id);
        byte[] pdfBytes = paymentOutWordTemplateService.generatePaymentOutPdf(payment);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "payment-out-" + payment.getReceiptNumber() + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    
    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.PAYMENT_OUT, action = Modules.DOWNLOAD)
    @Operation(summary = "Download payment-out as PDF", description = "Generate and download payment-out as PDF file")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        PaymentOutDto payment = paymentOutService.findById(id);
        byte[] pdfBytes = paymentOutWordTemplateService.generatePaymentOutPdf(payment);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "payment-out-" + payment.getReceiptNumber() + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}


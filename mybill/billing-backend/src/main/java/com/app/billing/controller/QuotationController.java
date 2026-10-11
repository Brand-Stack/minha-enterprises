package com.app.billing.controller;

import com.app.billing.dto.PageResponse;
import com.app.billing.dto.QuotationDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.BillPdfService;
import com.app.billing.service.QuotationService;
import com.app.billing.service.QuotationWordTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/quotations")
@RequiredArgsConstructor
@Tag(name = "Quotation Management", description = "Quotation CRUD operations")
public class QuotationController {
    
    private final QuotationService quotationService;
    private final QuotationWordTemplateService quotationWordTemplateService;
    private final BillPdfService billPdfService;
    
    @PostMapping
    @RequiresPermission(module = Modules.QUOTATION, action = Modules.CREATE)
    @Operation(summary = "Create quotation", description = "Create a new quotation")
    public ResponseEntity<QuotationDto> create(@Valid @RequestBody QuotationDto dto) {
        return new ResponseEntity<>(quotationService.create(dto), HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.QUOTATION, action = Modules.EDIT)
    @Operation(summary = "Update quotation", description = "Update existing quotation")
    public ResponseEntity<QuotationDto> update(@PathVariable String id, @Valid @RequestBody QuotationDto dto) {
        return ResponseEntity.ok(quotationService.update(id, dto));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get quotation by ID", description = "Retrieve quotation by ID")
    public ResponseEntity<QuotationDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(quotationService.findById(id));
    }
    
    @GetMapping
    @RequiresPermission(module = Modules.QUOTATION, action = Modules.VIEW)
    @Operation(summary = "Get all quotations", description = "Retrieve all quotations with pagination")
    public ResponseEntity<PageResponse<QuotationDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "quotationDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(quotationService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search quotations", description = "Search quotations by term")
    public ResponseEntity<PageResponse<QuotationDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "quotationDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(quotationService.search(searchTerm, page, size, sortBy, sortDir));
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.QUOTATION, action = Modules.DELETE)
    @Operation(summary = "Delete quotation", description = "Delete quotation by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        quotationService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/print")
    @RequiresPermission(module = Modules.QUOTATION, action = Modules.PRINT)
    @Operation(summary = "Print quotation preview", description = "Generate PDF for print preview with proper text wrapping (inline display)")
    public ResponseEntity<byte[]> printQuotation(@PathVariable String id) {
        try {
            QuotationDto quotation = quotationService.findById(id);
            log.info("Generating quotation PDF with text wrapping for quotation: {}", quotation.getQuotationNumber());
            // Use BillPdfService for proper text wrapping and layout
            byte[] pdfBytes = billPdfService.generateQuotationPdf(quotation);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "quotation-" + quotation.getQuotationNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            log.info("Successfully generated PDF for quotation: {}", quotation.getQuotationNumber());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("CRITICAL ERROR generating quotation PDF for quotation ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.QUOTATION, action = Modules.DOWNLOAD)
    @Operation(summary = "Download quotation as PDF", description = "Generate and download quotation as PDF file with proper text wrapping")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        try {
            QuotationDto quotation = quotationService.findById(id);
            log.info("Generating quotation PDF for download: {}", quotation.getQuotationNumber());
            // Use BillPdfService for proper text wrapping and layout
            byte[] pdfBytes = billPdfService.generateQuotationPdf(quotation);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "quotation-" + quotation.getQuotationNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            log.info("Successfully generated PDF for download: {}", quotation.getQuotationNumber());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("CRITICAL ERROR generating quotation PDF for download, quotation ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
}


package com.app.billing.controller;

import com.app.billing.dto.InvoiceDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.BillPdfService;
import com.app.billing.service.InvoicePdfService;
import com.app.billing.service.InvoiceService;
import com.app.billing.service.InvoiceWordTemplateService;
import com.app.billing.service.ReportExcelService;
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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice Management", description = "Invoice operations")
public class InvoiceController {
    
    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final InvoiceWordTemplateService invoiceWordTemplateService;
    private final BillPdfService billPdfService;
    private final ReportExcelService reportExcelService;
    
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    
    @PostMapping
    @RequiresPermission(module = Modules.BILLING, action = Modules.CREATE)
    @Operation(summary = "Create invoice", description = "Create a new invoice with auto-generated number")
    public ResponseEntity<InvoiceDto> create(@Valid @RequestBody InvoiceDto dto) {
        return new ResponseEntity<>(invoiceService.create(dto), HttpStatus.CREATED);
    }
    
    @GetMapping("/export")
    @RequiresPermission(module = Modules.BILLING, action = Modules.EXPORT)
    @Operation(summary = "Export invoices as Excel", description = "Export all invoices matching filters as Excel (for Transaction listing)")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String status) {
        try {
            List<InvoiceDto> data = invoiceService.findAllForExport(billType, partyId, invoiceNumber, status);
            byte[] excelBytes = reportExcelService.generateInvoiceListExcel(data);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE));
            headers.setContentDispositionFormData("attachment", "transactions_export.xlsx");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok().headers(headers).contentLength(excelBytes.length).body(excelBytes);
        } catch (Exception e) {
            log.error("Error generating invoice list Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by ID", description = "Retrieve invoice by ID")
    public ResponseEntity<InvoiceDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(invoiceService.findById(id));
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.BILLING, action = Modules.EDIT)
    @Operation(summary = "Update invoice", description = "Update existing invoice and auto-mark as CORRECTED if amount/rate changed")
    public ResponseEntity<InvoiceDto> update(@PathVariable String id, @Valid @RequestBody InvoiceDto dto) {
        return ResponseEntity.ok(invoiceService.update(id, dto));
    }
    
    @GetMapping
    @RequiresPermission(module = Modules.BILLING, action = Modules.VIEW)
    @Operation(summary = "Get all invoices", description = "Retrieve all invoices with pagination and optional filters")
    public ResponseEntity<PageResponse<InvoiceDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(invoiceService.findAll(page, size, sortBy, sortDir, billType, partyId, invoiceNumber, status));
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.BILLING, action = Modules.DELETE)
    @Operation(summary = "Delete invoice by ID", description = "Delete invoice by internal ID and restore stock")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/number/{invoiceNumber}")
    @RequiresPermission(module = Modules.BILLING, action = Modules.DELETE)
    @Operation(summary = "Delete invoice by invoice number", description = "Delete invoice by invoice number and restore stock")
    public ResponseEntity<Void> deleteByInvoiceNumber(@PathVariable String invoiceNumber) {
        invoiceService.deleteByInvoiceNumber(invoiceNumber);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/return")
    @RequiresPermission(module = Modules.BILLING, action = Modules.EDIT)
    @Operation(summary = "Return invoice", description = "Return an invoice - updates all fields, sets status to RETURNED and restores stock. Only allowed for GST invoices.")
    public ResponseEntity<InvoiceDto> returnInvoice(@PathVariable String id, @Valid @RequestBody InvoiceDto dto) {
        return ResponseEntity.ok(invoiceService.returnInvoice(id, dto));
    }
    
    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.BILLING, action = Modules.DOWNLOAD)
    @Operation(summary = "Download invoice as PDF", description = "Generate and download invoice as PDF file with proper text wrapping")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        try {
            InvoiceDto invoice = invoiceService.findById(id);
            log.info("Generating invoice PDF with text wrapping for download: {}", invoice.getInvoiceNumber());
            // Use BillPdfService for proper text wrapping and layout
            byte[] pdfBytes = billPdfService.generateInvoicePdf(invoice);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "invoice-" + invoice.getInvoiceNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            // CORS headers are handled by global CORS configuration in SecurityConfig
            // Only expose Content-Disposition header for client-side filename extraction
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            log.info("Successfully generated PDF for download: {}", invoice.getInvoiceNumber());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("CRITICAL ERROR generating invoice PDF for download, invoice ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/{id}/print")
    @RequiresPermission(module = Modules.BILLING, action = Modules.PRINT)
    @Operation(summary = "Print invoice preview", description = "Generate PDF for print preview with proper text wrapping (inline display)")
    public ResponseEntity<byte[]> printInvoice(@PathVariable String id) {
        try {
            InvoiceDto invoice = invoiceService.findById(id);
            log.info("Generating invoice PDF with text wrapping for invoice: {}", invoice.getInvoiceNumber());
            // Use BillPdfService for proper text wrapping and layout
            byte[] pdfBytes = billPdfService.generateInvoicePdf(invoice);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "invoice-" + invoice.getInvoiceNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            log.info("Successfully generated PDF for invoice: {}", invoice.getInvoiceNumber());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("CRITICAL ERROR generating invoice PDF for invoice ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/{id}/word-template")
    @Operation(summary = "Generate invoice from Word template", description = "Generate invoice PDF using Word template (XDocReport + Velocity)")
    public ResponseEntity<byte[]> generateFromWordTemplate(@PathVariable String id) {
        try {
            InvoiceDto invoice = invoiceService.findById(id);
            byte[] pdfBytes = invoiceWordTemplateService.generateInvoicePdf(invoice);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "invoice-" + invoice.getInvoiceNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating invoice from Word template: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}


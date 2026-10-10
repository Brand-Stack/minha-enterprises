package com.app.billing.controller;

import com.app.billing.dto.OnboardQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.OnboardEmailService;
import com.app.billing.service.OnboardQuotationExcelService;
import com.app.billing.service.OnboardQuotationPdfService;
import com.app.billing.service.OnboardQuotationService;
import com.app.billing.util.AttachmentFilenameUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/onboard-quotations")
@RequiredArgsConstructor
@Tag(name = "Onboard Quotation", description = "Onboard Prospective Customer Quotation Management")
public class OnboardQuotationController {

    private final OnboardQuotationService service;
    private final OnboardQuotationPdfService pdfService;
    private final OnboardQuotationExcelService excelService;
    private final OnboardEmailService emailService;

    @PostMapping
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.CREATE)
    @Operation(summary = "Create onboard quotation")
    public ResponseEntity<OnboardQuotationDto> create(@Valid @RequestBody OnboardQuotationDto dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.EDIT)
    @Operation(summary = "Update onboard quotation")
    public ResponseEntity<OnboardQuotationDto> update(@PathVariable String id,
                                                    @Valid @RequestBody OnboardQuotationDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @GetMapping("/{id}")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.VIEW)
    @Operation(summary = "Get onboard quotation by ID")
    public ResponseEntity<OnboardQuotationDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.VIEW)
    @Operation(summary = "Get all onboard quotations (paginated, with optional filters and search)")
    public ResponseEntity<PageResponse<OnboardQuotationDto>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "effectiveDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (effectiveFrom != null || effectiveTo != null || (status != null && !status.trim().isEmpty())
                || (searchTerm != null && !searchTerm.trim().isEmpty())) {
            return ResponseEntity.ok(service.findWithFiltersAndSearch(effectiveFrom, effectiveTo, status, searchTerm,
                    page, size, sortBy, sortDir));
        }
        return ResponseEntity.ok(service.findAll(page, size, sortBy, sortDir));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.DELETE)
    @Operation(summary = "Delete onboard quotation")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/print")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.PRINT)
    @Operation(summary = "Print onboard quotation (inline PDF)")
    public ResponseEntity<byte[]> print(@PathVariable String id) {
        try {
            OnboardQuotationDto dto = service.findById(id);
            byte[] pdf = pdfService.generate(dto);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.inlineUtf8(AttachmentFilenameUtil.courierQuotationPdf(dto.getQuotationNumber(), dto.getCustomerName())));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Exception e) {
            log.error("Error generating print PDF for onboard quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.DOWNLOAD)
    @Operation(summary = "Download onboard quotation as PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        try {
            OnboardQuotationDto dto = service.findById(id);
            byte[] pdf = pdfService.generate(dto);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.courierQuotationPdf(dto.getQuotationNumber(), dto.getCustomerName())));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Exception e) {
            log.error("Error generating download PDF for onboard quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @GetMapping("/{id}/export/excel")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.EXPORT)
    @Operation(summary = "Export onboard quotation as Excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String id) {
        try {
            OnboardQuotationDto dto = service.findById(id);
            byte[] excel = excelService.generateExcel(dto);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.courierQuotationXlsx(dto.getQuotationNumber(), dto.getCustomerName())));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(excel.length).body(excel);
        } catch (Exception e) {
            log.error("Error generating Excel for onboard quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @PostMapping("/{id}/email")
    @RequiresPermission(module = Modules.ONBOARD_QUOTATION, action = Modules.EMAIL)
    @Operation(summary = "Send onboard quotation via email")
    public ResponseEntity<Map<String, String>> sendEmail(@PathVariable String id, @RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Recipient email address is required"));
        }
        try {
            emailService.sendOnboardQuotationEmail(id, email.trim());
            return ResponseEntity.ok(Map.of("message", "Email sent successfully to " + email.trim()));
        } catch (Exception e) {
            log.error("Failed to send email for onboard quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to send email: " + e.getMessage()));
        }
    }
}

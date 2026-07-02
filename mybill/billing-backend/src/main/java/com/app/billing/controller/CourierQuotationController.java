package com.app.billing.controller;

import com.app.billing.dto.CourierQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CourierQuotationExcelService;
import com.app.billing.service.CourierEmailService;
import com.app.billing.service.CourierQuotationPdfService;
import com.app.billing.service.CourierQuotationService;
import com.app.billing.util.AttachmentFilenameUtil;
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
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/courier-quotations")
@RequiredArgsConstructor
@Tag(name = "Courier Quotation", description = "Courier Rate Quotation Management")
public class CourierQuotationController {

    private final CourierQuotationService service;
    private final CourierQuotationPdfService pdfService;
    private final CourierQuotationExcelService excelService;
    private final CourierEmailService courierEmailService;

    @PostMapping
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.CREATE)
    @Operation(summary = "Create courier quotation")
    public ResponseEntity<CourierQuotationDto> create(@Valid @RequestBody CourierQuotationDto dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EDIT)
    @Operation(summary = "Update courier quotation")
    public ResponseEntity<CourierQuotationDto> update(@PathVariable String id,
            @Valid @RequestBody CourierQuotationDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get courier quotation by ID")
    public ResponseEntity<CourierQuotationDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.VIEW)
    @Operation(summary = "Get all courier quotations (paginated, with optional filters and optional search text)")
    public ResponseEntity<PageResponse<CourierQuotationDto>> findAll(
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

    @GetMapping("/search")
    @Operation(summary = "Search courier quotations")
    public ResponseEntity<PageResponse<CourierQuotationDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "effectiveDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(service.search(searchTerm, page, size, sortBy, sortDir));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.DELETE)
    @Operation(summary = "Delete courier quotation")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/approve")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EDIT)
    @Operation(summary = "Approve a DRAFT quotation")
    public ResponseEntity<CourierQuotationDto> approve(@PathVariable String id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PatchMapping("/{id}/activate")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EDIT)
    @Operation(summary = "Activate an APPROVED quotation")
    public ResponseEntity<CourierQuotationDto> activate(@PathVariable String id) {
        return ResponseEntity.ok(service.activate(id));
    }

    @PatchMapping("/{id}/expire")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EDIT)
    @Operation(summary = "Expire a quotation")
    public ResponseEntity<CourierQuotationDto> expire(@PathVariable String id) {
        return ResponseEntity.ok(service.expire(id));
    }

    @GetMapping("/{id}/print")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.PRINT)
    @Operation(summary = "Print courier quotation (inline PDF)")
    public ResponseEntity<byte[]> print(@PathVariable String id) {
        try {
            CourierQuotationDto dto = service.findById(id);
            byte[] pdf = pdfService.generate(dto);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.inlineUtf8(AttachmentFilenameUtil.courierQuotationPdf(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Exception e) {
            log.error("Error generating print PDF for courier quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.DOWNLOAD)
    @Operation(summary = "Download courier quotation as PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        try {
            CourierQuotationDto dto = service.findById(id);
            byte[] pdf = pdfService.generate(dto);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.courierQuotationPdf(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Exception e) {
            log.error("Error generating download PDF for courier quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @GetMapping("/{id}/export/excel")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EXPORT)
    @Operation(summary = "Export courier quotation as Excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String id) {
        try {
            CourierQuotationDto dto = service.findById(id);
            byte[] excel = excelService.generateExcel(dto);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.courierQuotationXlsx(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(excel.length).body(excel);
        } catch (Exception e) {
            log.error("Error generating Excel for courier quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @GetMapping("/report")
    @Operation(summary = "Search quotations for report processing")
    public ResponseEntity<PageResponse<CourierQuotationDto>> searchReport(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "effectiveDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        return ResponseEntity.ok(service.searchReport(customerId, startDate, endDate, status, page, size, sortBy, sortDir));
    }

    @GetMapping("/export/all")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EXPORT)
    @Operation(summary = "Export all displayed quotations to Excel")
    public ResponseEntity<byte[]> exportAll(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size,
            @RequestParam(defaultValue = "effectiveDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PageResponse<CourierQuotationDto> response;
        if (effectiveFrom != null || effectiveTo != null || (status != null && !status.trim().isEmpty())
                || (searchTerm != null && !searchTerm.trim().isEmpty())) {
            String st = searchTerm != null && !searchTerm.trim().isEmpty() ? searchTerm.trim() : null;
            response = service.findWithFiltersAndSearch(effectiveFrom, effectiveTo, status, st, page, size, sortBy,
                    sortDir);
        } else {
            response = service.findAll(page, size, sortBy, sortDir);
        }
        List<CourierQuotationDto> list = response.getContent();
        byte[] excel = excelService.generateListExcel(list);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = "Quotations_" + dateStr + ".xlsx";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment", filename);
        h.add("Access-Control-Expose-Headers", "Content-Disposition");
        return ResponseEntity.ok().headers(h).contentLength(excel.length).body(excel);
    }

    @GetMapping("/export/report")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EXPORT)
    @Operation(summary = "Export quotation report to Excel")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {
        PageResponse<CourierQuotationDto> response = service.searchReport(
                customerId, startDate, endDate, status, 0, 10_000, "effectiveDate", "desc");
        List<CourierQuotationDto> list = response.getContent();
        byte[] excel = excelService.generateListExcel(list);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = "Quotation_Report_" + dateStr + ".xlsx";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment", filename);
        h.add("Access-Control-Expose-Headers", "Content-Disposition");
        return ResponseEntity.ok().headers(h).contentLength(excel.length).body(excel);
    }

    @GetMapping("/export/report/pdf")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EXPORT)
    @Operation(summary = "Export quotation report as PDF")
    public ResponseEntity<byte[]> exportReportPdf(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {
        PageResponse<CourierQuotationDto> response = service.searchReport(
                customerId, startDate, endDate, status, 0, 10_000, "effectiveDate", "desc");
        List<CourierQuotationDto> list = response.getContent();
        byte[] pdf = pdfService.generateReportPdf(list, startDate, endDate);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = "Quotation_Report_" + dateStr + ".pdf";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDispositionFormData("attachment", filename);
        h.add("Access-Control-Expose-Headers", "Content-Disposition");
        return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
    }

    @PostMapping("/{id}/email")
    @RequiresPermission(module = Modules.COURIER_QUOTATION, action = Modules.EMAIL)
    @Operation(summary = "Send courier quotation by email (PDF attachment)")
    public ResponseEntity<?> sendEmail(@PathVariable String id) {
        try {
            courierEmailService.sendCourierRateQuotationEmail(id);
            return ResponseEntity.ok(Map.of("message", "Email sent successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            log.warn("Email send failed (not found): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending courier quotation email for {}: {}", id, e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to send email";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", msg));
        }
    }
}

package com.app.billing.controller;

import com.app.billing.dto.AwbShipmentLookupDto;
import com.app.billing.dto.SmallClientEntryDto;
import com.app.billing.dto.MonthlyCourierEmailRequestDto;
import com.app.billing.dto.SmallClientEntryQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CourierEmailService;
import com.app.billing.service.SmallClientEntryService;
import com.app.billing.service.SmallClientEntryQuotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.app.billing.model.ZoneConfiguration;
import com.app.billing.util.AttachmentFilenameUtil;

@Slf4j
@RestController
@RequestMapping("/small-client-entries")
@RequiredArgsConstructor
@Tag(name = "Small Client Entry", description = "Billing entries for small / retail clients")
public class SmallClientEntryController {

    private final SmallClientEntryQuotationService quotationService;
    private final SmallClientEntryService entryService;
    private final com.app.billing.service.SmallClientEntryQuotationPdfService pdfService;
    private final com.app.billing.service.SmallClientEntryQuotationWordService wordService;
    private final CourierEmailService courierEmailService;
    private final com.app.billing.service.MonthlyShipmentBreakupExcelService monthlyShipmentBreakupExcelService;
    private final com.app.billing.dao.ZoneConfigurationRepository zoneConfigurationRepository;

    @PostMapping
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.CREATE)
    @Operation(summary = "Create Small Client Entry quotation")
    public ResponseEntity<SmallClientEntryQuotationDto> createQuotation(
            @Valid @RequestBody SmallClientEntryQuotationDto dto) {
        return new ResponseEntity<>(quotationService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.EDIT)
    @Operation(summary = "Update Small Client Entry quotation")
    public ResponseEntity<SmallClientEntryQuotationDto> updateQuotation(@PathVariable String id,
            @Valid @RequestBody SmallClientEntryQuotationDto dto) {
        return ResponseEntity.ok(quotationService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.EDIT)
    @Operation(summary = "Update Small Client Entry quotation status (Courier Report inline editing)")
    public ResponseEntity<SmallClientEntryQuotationDto> updateQuotationStatus(
            @PathVariable String id, @RequestBody java.util.Map<String, Object> statusUpdate) {
        return ResponseEntity.ok(quotationService.updateAmountStatusAndDescription(id, statusUpdate));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Small Client Entry quotation by ID")
    public ResponseEntity<SmallClientEntryQuotationDto> findQuotationById(@PathVariable String id) {
        return ResponseEntity.ok(quotationService.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.VIEW)
    @Operation(summary = "Get Small Client Entry quotations (paginated): fixed sort by createdAt desc; optional filters and global search")
    public ResponseEntity<PageResponse<SmallClientEntryQuotationDto>> findAllQuotations(
            @RequestParam(required = false) String shopId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String amountStatus,
            @RequestParam(required = false) Boolean isDownloaded,
            @RequestParam(required = false) Boolean invoiceGenerated,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Integer invoiceMonth,
            @RequestParam(required = false) Integer invoiceYear,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate invoiceDateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate invoiceDateTo,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(quotationService.search(shopId, title, customerId, zone, fromDate, toDate, month, year,
                trackingNumber, amountStatus, isDownloaded, invoiceGenerated, invoiceNumber, customerName, invoiceMonth, invoiceYear,
                invoiceDateFrom, invoiceDateTo, description, search, sortBy, sortDir, page, size));
    }

    @GetMapping("/{id}/breakup-pdf")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.DOWNLOAD)
    @Operation(summary = "Shipment breakup only as PDF (optional entryIds=comma-separated to export filtered rows)")
    public ResponseEntity<byte[]> downloadBreakupPdf(@PathVariable String id,
            @RequestParam(required = false) String entryIds,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight) {
        try {
            SmallClientEntryQuotationDto dto = quotationService.findById(id);
            List<SmallClientEntryDto> all = entryService.findByQuotationId(id);
            List<SmallClientEntryDto> entries = filterEntriesByOptionalIds(all, entryIds);
            Map<String, String> zoneIdToName = buildZoneIdToNameMap();
            byte[] pdf = pdfService.generateShipmentBreakupOnly(dto, entries, zoneIdToName, includeAmount, includeWeight);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.smallClientBreakupPdf(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Exception e) {
            log.error("Breakup PDF failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/breakup-excel")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.EXPORT)
    @Operation(summary = "Shipment breakup as Excel (optional entryIds=comma-separated for filtered export)")
    public ResponseEntity<byte[]> downloadBreakupExcel(@PathVariable String id,
            @RequestParam(required = false) String entryIds,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight) {
        try {
            List<SmallClientEntryDto> all = entryService.findByQuotationId(id);
            List<SmallClientEntryDto> entries = filterEntriesByOptionalIds(all, entryIds);
            Map<String, String> zoneIdToName = buildZoneIdToNameMap();
            SmallClientEntryQuotationDto dto = quotationService.findById(id);
            byte[] xlsx = monthlyShipmentBreakupExcelService.generateForSmallClient(entries, includeAmount, includeWeight, zoneIdToName);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.smallClientInvoiceXlsx(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(xlsx.length).body(xlsx);
        } catch (Exception e) {
            log.error("Breakup Excel failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static List<SmallClientEntryDto> filterEntriesByOptionalIds(List<SmallClientEntryDto> all,
            String entryIdsCsv) {
        if (entryIdsCsv == null || entryIdsCsv.isBlank()) {
            return all;
        }
        Set<String> wanted = java.util.Arrays.stream(entryIdsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return all.stream()
                .filter(e -> e.getId() != null && wanted.contains(e.getId()))
                .toList();
    }

    private Map<String, String> buildZoneIdToNameMap() {
        return zoneConfigurationRepository.findByIsDeletedFalse().stream()
                .collect(Collectors.toMap(ZoneConfiguration::getId,
                        z -> z.getZoneName() != null ? z.getZoneName() : z.getId(),
                        (a, b) -> a));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.DELETE)
    @Operation(summary = "Delete Small Client Entry quotation")
    public ResponseEntity<Void> deleteQuotation(@PathVariable String id) {
        quotationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/report/{id}")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY_REPORT, action = Modules.DELETE)
    @Operation(summary = "Delete small client entry from report")
    public ResponseEntity<Void> deleteFromReport(@PathVariable String id) {
        quotationService.deleteFromReport(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate-invoice")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.CREATE)
    @Operation(summary = "Explicitly generate and persist invoice for small client entry")
    public ResponseEntity<SmallClientEntryQuotationDto> generateInvoice(@PathVariable String id) {
        return ResponseEntity.ok(quotationService.generateInvoice(id));
    }

    // --- Entry Endpoints ---

    @GetMapping("/{id}/entries")
    @Operation(summary = "Get all entries for a Small Client Entry quotation")
    public ResponseEntity<List<SmallClientEntryDto>> getEntries(@PathVariable String id) {
        return ResponseEntity.ok(entryService.findByQuotationId(id));
    }

    @PostMapping("/{id}/entries")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.CREATE)
    @Operation(summary = "Add entry to a Small Client Entry quotation")
    public ResponseEntity<SmallClientEntryDto> addEntry(@PathVariable String id,
            @Valid @RequestBody SmallClientEntryDto dto) {
        return new ResponseEntity<>(entryService.create(id, dto), HttpStatus.CREATED);
    }

    @PutMapping("/entries/{entryId}")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.EDIT)
    @Operation(summary = "Update entry")
    public ResponseEntity<SmallClientEntryDto> updateEntry(@PathVariable String entryId,
            @Valid @RequestBody SmallClientEntryDto dto) {
        return ResponseEntity.ok(entryService.update(entryId, dto));
    }

    @DeleteMapping("/entries/{entryId}")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.DELETE)
    @Operation(summary = "Delete entry")
    public ResponseEntity<Void> deleteEntry(@PathVariable String entryId) {
        entryService.delete(entryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/calculate")
    @Operation(summary = "Calculate amount for entry based on cumulative slab model")
    public ResponseEntity<Double> calculateAmount(@PathVariable String id,
            @RequestParam(required = true) String zone,
            @RequestParam(required = false) String rateType,
            @RequestParam(required = false, defaultValue = "") String courierType,
            @RequestParam(required = true) Double weight) {
        if (zone == null || zone.isBlank() || weight == null || weight <= 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(entryService.calculateDynamicAmount(id, zone, rateType, courierType, weight));
    }

    @GetMapping("/{id}/pdf")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.DOWNLOAD)
    @Operation(summary = "Download Small Client Entry quotation as PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false) Boolean includeGst,
            @RequestParam(required = false) Boolean includeFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeGstAndFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight,
            @RequestParam(required = false, defaultValue = "true") boolean includeBreakup,
            @RequestParam(required = false, defaultValue = "DOWNLOAD") String invoiceAction) {
        try {
            SmallClientEntryQuotationDto dto = quotationService.findById(id);
            quotationService.recordInvoiceActivity(id, invoiceAction);
            boolean gst = includeGst != null ? includeGst : (dto.getIncludeGst() != null ? dto.getIncludeGst() : includeGstAndFuel);
            boolean fuel = includeFuel != null ? includeFuel : (dto.getIncludeFuel() != null ? dto.getIncludeFuel() : includeGstAndFuel);
            byte[] pdf = pdfService.generate(dto, includeAmount, gst, fuel, includeWeight, includeBreakup);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.smallClientInvoicePdf(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getName();
            log.error("PDF download failed for Small Client Entry quotation id={}: {}", id, msg, t);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", msg.length() > 500 ? msg.substring(0, 500) : msg)
                    .build();
        }
    }

    @PostMapping("/{id}/invoice-activity")
    @Operation(summary = "Log invoice view, print, or download activity")
    public ResponseEntity<SmallClientEntryQuotationDto> recordInvoiceActivity(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "VIEW") String action) {
        return ResponseEntity.ok(quotationService.recordInvoiceActivity(id, action));
    }

    @GetMapping("/{id}/word")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.DOWNLOAD)
    @Operation(summary = "Download Small Client Entry quotation as Word")
    public ResponseEntity<byte[]> downloadWord(@PathVariable String id,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false) Boolean includeGst,
            @RequestParam(required = false) Boolean includeFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeGstAndFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight) {
        try {
            SmallClientEntryQuotationDto dto = quotationService.findById(id);
            quotationService.recordInvoiceActivity(id, "DOWNLOAD");
            boolean gst = includeGst != null ? includeGst : includeGstAndFuel;
            boolean fuel = includeFuel != null ? includeFuel : includeGstAndFuel;
            byte[] word = wordService.generate(dto, includeAmount, gst, fuel, includeWeight);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.smallClientInvoiceDocx(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(word.length).body(word);
        } catch (Exception e) {
            log.error("Error generating Word doc for Small Client Entry quotation {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage()).build();
        }
    }

    @GetMapping("/shipments/awb/{awb}")
    @Operation(summary = "Lookup shipment by AWB / tracking number")
    public ResponseEntity<AwbShipmentLookupDto> lookupAwb(@PathVariable String awb) {
        return ResponseEntity.ok(entryService.lookupByTrackingNumber(awb));
    }

    @PostMapping("/{id}/email")
    @RequiresPermission(module = Modules.SMALL_CLIENT_ENTRY, action = Modules.EMAIL)
    @Operation(summary = "Send Small Client Entry booking details by email")
    public ResponseEntity<?> sendEmail(@PathVariable String id, @RequestBody(required = false) MonthlyCourierEmailRequestDto request) {
        try {
            courierEmailService.sendSmallClientEntryEmail(id, request != null ? request : new MonthlyCourierEmailRequestDto());
            return ResponseEntity.ok(Map.of("message", "Email sent successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            log.warn("Monthly email send failed (not found): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending Small Client Entry email for {}: {}", id, e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to send email";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", msg));
        }
    }
}


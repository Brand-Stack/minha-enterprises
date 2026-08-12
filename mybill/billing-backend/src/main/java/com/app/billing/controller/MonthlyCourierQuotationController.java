package com.app.billing.controller;

import com.app.billing.dto.AwbShipmentLookupDto;
import com.app.billing.dto.MonthlyCourierEntryDto;
import com.app.billing.dto.MonthlyCourierEmailRequestDto;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CourierEmailService;
import com.app.billing.service.MonthlyCourierEntryService;
import com.app.billing.service.MonthlyCourierQuotationService;
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
@RequestMapping({"/monthly-courier-quotations", "/client-entries"})
@RequiredArgsConstructor
@Tag(name = "Client Entry (Monthly Courier)", description = "Client monthly courier records — /monthly-courier-quotations kept for compatibility")
public class MonthlyCourierQuotationController {

    private final MonthlyCourierQuotationService quotationService;
    private final MonthlyCourierEntryService entryService;
    private final com.app.billing.service.MonthlyCourierQuotationPdfService pdfService;
    private final com.app.billing.service.MonthlyCourierQuotationWordService wordService;
    private final CourierEmailService courierEmailService;
    private final com.app.billing.service.MonthlyShipmentBreakupExcelService monthlyShipmentBreakupExcelService;
    private final com.app.billing.dao.ZoneConfigurationRepository zoneConfigurationRepository;
    private final com.app.billing.service.MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;

    @PostMapping
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.CREATE)
    @Operation(summary = "Create monthly courier quotation")
    public ResponseEntity<MonthlyCourierQuotationDto> createQuotation(
            @Valid @RequestBody MonthlyCourierQuotationDto dto) {
        return new ResponseEntity<>(quotationService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.EDIT)
    @Operation(summary = "Update monthly courier quotation")
    public ResponseEntity<MonthlyCourierQuotationDto> updateQuotation(@PathVariable String id,
            @Valid @RequestBody MonthlyCourierQuotationDto dto) {
        return ResponseEntity.ok(quotationService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.EDIT)
    @Operation(summary = "Update monthly courier quotation status (Courier Report inline editing)")
    public ResponseEntity<MonthlyCourierQuotationDto> updateQuotationStatus(
            @PathVariable String id, @RequestBody java.util.Map<String, String> statusUpdate) {
        return ResponseEntity.ok(quotationService.updateAmountStatusAndDescription(id, statusUpdate));
    }

    @GetMapping("/{id}")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.VIEW)
    @Operation(summary = "Get monthly courier quotation by ID")
    public ResponseEntity<MonthlyCourierQuotationDto> findQuotationById(@PathVariable String id) {
        return ResponseEntity.ok(quotationService.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.VIEW)
    @Operation(summary = "Get monthly courier quotations (paginated): fixed sort by createdAt desc; optional filters and global search")
    public ResponseEntity<PageResponse<MonthlyCourierQuotationDto>> findAllQuotations(
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
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Integer invoiceMonth,
            @RequestParam(required = false) Integer invoiceYear,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate invoiceDateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate invoiceDateTo,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(quotationService.search(shopId, title, customerId, zone, fromDate, toDate, month, year,
                trackingNumber, amountStatus, isDownloaded, invoiceNumber, customerName, invoiceMonth, invoiceYear,
                invoiceDateFrom, invoiceDateTo, description, search, page, size));
    }

    @GetMapping("/{id}/breakup-pdf")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.DOWNLOAD)
    @Operation(summary = "Shipment breakup only as PDF (optional entryIds=comma-separated to export filtered rows)")
    public ResponseEntity<byte[]> downloadBreakupPdf(@PathVariable String id,
            @RequestParam(required = false) String entryIds,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight) {
        try {
            MonthlyCourierQuotationDto dto = quotationService.findById(id);
            List<MonthlyCourierEntryDto> all = entryService.findByQuotationId(id);
            List<MonthlyCourierEntryDto> entries = filterEntriesByOptionalIds(all, entryIds);
            Map<String, String> zoneIdToName = buildZoneIdToNameMap();
            byte[] pdf = pdfService.generateShipmentBreakupOnly(dto, entries, zoneIdToName, includeAmount, includeWeight);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.monthlyBreakupPdf(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Exception e) {
            log.error("Breakup PDF failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/breakup-excel")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.EXPORT)
    @Operation(summary = "Shipment breakup as Excel (optional entryIds=comma-separated for filtered export)")
    public ResponseEntity<byte[]> downloadBreakupExcel(@PathVariable String id,
            @RequestParam(required = false) String entryIds,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight) {
        try {
            List<MonthlyCourierEntryDto> all = entryService.findByQuotationId(id);
            List<MonthlyCourierEntryDto> entries = filterEntriesByOptionalIds(all, entryIds);
            Map<String, String> zoneIdToName = buildZoneIdToNameMap();
            MonthlyCourierQuotationDto dto = quotationService.findById(id);

            com.app.billing.service.MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext calcCtx =
                    com.app.billing.service.MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                            dto.getCustomerId(),
                            dto.getFuelChargePercentage(),
                            dto.getFovCharges(),
                            dto.getGstPercentage(),
                            dto.getIncludeFuel() != null ? dto.getIncludeFuel() : true,
                            dto.getIncludeGst() != null ? dto.getIncludeGst() : true,
                            dto.getIncludeFov() != null ? dto.getIncludeFov() : true);
            List<com.app.billing.model.MonthlyCourierEntry> monthlyEntries = entries.stream()
                    .map(d -> {
                        com.app.billing.model.MonthlyCourierEntry e = new com.app.billing.model.MonthlyCourierEntry();
                        e.setAmount(d.getAmount());
                        e.setAdditionalCharges(d.getAdditionalCharges());
                        e.setFuelApplicable(d.getFuelApplicable());
                        e.setGstApplicable(d.getGstApplicable());
                        e.setFovApplicable(d.getFovApplicable());
                        return e;
                    })
                    .toList();
            com.app.billing.service.MonthlyCourierInvoiceGrandTotalService.PdfInvoiceTotals totals =
                    invoiceGrandTotalService.computePdfTotals(calcCtx, monthlyEntries);

            byte[] xlsx = monthlyShipmentBreakupExcelService.generate(entries, includeAmount, includeWeight, zoneIdToName, totals);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.monthlyInvoiceXlsx(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(xlsx.length).body(xlsx);
        } catch (Exception e) {
            log.error("Breakup Excel failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static List<MonthlyCourierEntryDto> filterEntriesByOptionalIds(List<MonthlyCourierEntryDto> all,
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
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.DELETE)
    @Operation(summary = "Delete monthly courier quotation")
    public ResponseEntity<Void> deleteQuotation(@PathVariable String id) {
        quotationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Entry Endpoints ---

    @GetMapping("/{id}/entries")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.VIEW)
    @Operation(summary = "Get entries for a monthly courier quotation (optional paginated)")
    public ResponseEntity<?> getEntries(
            @PathVariable String id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String fields) {
        if (page != null && size != null) {
            return ResponseEntity.ok(entryService.findByQuotationId(id, page, size, search, fields));
        }
        return ResponseEntity.ok(entryService.findByQuotationId(id, search, fields));
    }

    @PostMapping("/{id}/entries")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.CREATE)
    @Operation(summary = "Add entry to a monthly courier quotation")
    public ResponseEntity<MonthlyCourierEntryDto> addEntry(@PathVariable String id,
            @Valid @RequestBody MonthlyCourierEntryDto dto) {
        return new ResponseEntity<>(entryService.create(id, dto), HttpStatus.CREATED);
    }

    @PutMapping("/entries/{entryId}")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.EDIT)
    @Operation(summary = "Update entry")
    public ResponseEntity<MonthlyCourierEntryDto> updateEntry(@PathVariable String entryId,
            @Valid @RequestBody MonthlyCourierEntryDto dto) {
        return ResponseEntity.ok(entryService.update(entryId, dto));
    }

    @DeleteMapping("/entries/{entryId}")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.DELETE)
    @Operation(summary = "Delete entry")
    public ResponseEntity<Void> deleteEntry(@PathVariable String entryId) {
        entryService.delete(entryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/calculate")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.VIEW)
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
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.DOWNLOAD)
    @Operation(summary = "Download monthly courier quotation as PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false) Boolean includeGst,
            @RequestParam(required = false) Boolean includeFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeGstAndFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight,
            @RequestParam(required = false, defaultValue = "true") boolean includeBreakup,
            @RequestParam(required = false, defaultValue = "DOWNLOAD") String invoiceAction) {
        try {
            MonthlyCourierQuotationDto dto = quotationService.findById(id);
            quotationService.recordInvoiceActivity(id, invoiceAction);
            boolean gst = includeGst != null ? includeGst : includeGstAndFuel;
            boolean fuel = includeFuel != null ? includeFuel : includeGstAndFuel;
            byte[] pdf = pdfService.generate(dto, includeAmount, gst, fuel, includeWeight, includeBreakup);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.monthlyInvoicePdf(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(pdf.length).body(pdf);
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getName();
            log.error("PDF download failed for monthly courier quotation id={}: {}", id, msg, t);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", msg.length() > 500 ? msg.substring(0, 500) : msg)
                    .build();
        }
    }

    @PostMapping("/{id}/invoice-activity")
    @Operation(summary = "Log invoice view, print, or download activity")
    public ResponseEntity<MonthlyCourierQuotationDto> recordInvoiceActivity(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "VIEW") String action) {
        return ResponseEntity.ok(quotationService.recordInvoiceActivity(id, action));
    }

    @GetMapping("/{id}/word")
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.DOWNLOAD)
    @Operation(summary = "Download monthly courier quotation as Word")
    public ResponseEntity<byte[]> downloadWord(@PathVariable String id,
            @RequestParam(required = false, defaultValue = "true") boolean includeAmount,
            @RequestParam(required = false) Boolean includeGst,
            @RequestParam(required = false) Boolean includeFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeGstAndFuel,
            @RequestParam(required = false, defaultValue = "true") boolean includeWeight) {
        try {
            MonthlyCourierQuotationDto dto = quotationService.findById(id);
            quotationService.recordInvoiceActivity(id, "DOWNLOAD");
            boolean gst = includeGst != null ? includeGst : includeGstAndFuel;
            boolean fuel = includeFuel != null ? includeFuel : includeGstAndFuel;
            byte[] word = wordService.generate(dto, includeAmount, gst, fuel, includeWeight);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            h.setContentDisposition(AttachmentFilenameUtil.attachmentUtf8(AttachmentFilenameUtil.monthlyInvoiceDocx(dto)));
            h.add("Access-Control-Expose-Headers", "Content-Disposition");
            return ResponseEntity.ok().headers(h).contentLength(word.length).body(word);
        } catch (Exception e) {
            log.error("Error generating Word doc for monthly courier quotation {}: {}", id, e.getMessage(), e);
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
    @RequiresPermission(module = Modules.CLIENT_ENTRY, action = Modules.EMAIL)
    @Operation(summary = "Send monthly courier booking details by email")
    public ResponseEntity<?> sendEmail(@PathVariable String id, @RequestBody(required = false) MonthlyCourierEmailRequestDto request) {
        try {
            courierEmailService.sendMonthlyCourierEmail(id, request != null ? request : new MonthlyCourierEmailRequestDto());
            return ResponseEntity.ok(Map.of("message", "Email sent successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            log.warn("Monthly email send failed (not found): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending monthly courier email for {}: {}", id, e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to send email";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", msg));
        }
    }
}

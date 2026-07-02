package com.app.billing.controller;

import com.app.billing.model.GstInReport;
import com.app.billing.dao.GstInReportRepository;
import com.app.billing.model.GstOutRecord;
import com.app.billing.service.GstOutRecordService;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.service.MonthlyCourierQuotationService;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.service.MonthlyCourierInvoiceGrandTotalService;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.PermissionEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/gst-report")
@RequiredArgsConstructor
@Tag(name = "GST Report", description = "Endpoints for GST IN, GST OUT, and GST Ledger reports")
public class GstReportController {

    private final GstOutRecordService gstOutRecordService;
    private final MonthlyCourierQuotationService quotationService;
    private final MonthlyCourierEntryRepository entryRepository;
    private final MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;
    private final GstInReportRepository gstInReportRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;

    // GST OUT Endpoints
    @GetMapping("/gst-out")
    @RequiresPermission(module = Modules.GST_REPORT, action = "view_gst_out")
    @Operation(summary = "Get all manual GST OUT records")
    public ResponseEntity<List<GstOutRecord>> getGstOutRecords() {
        return ResponseEntity.ok(gstOutRecordService.findAll());
    }

    @PostMapping("/gst-out")
    @Operation(summary = "Save or update a manual GST OUT record")
    public ResponseEntity<GstOutRecord> saveGstOutRecord(@RequestBody GstOutRecord record) {
        if (record.getId() == null || record.getId().isBlank()) {
            if (!permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "add_gst_out")) {
                throw new AccessDeniedException("You do not have permission to add GST OUT records.");
            }
        } else {
            if (!permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "edit_gst_out")) {
                throw new AccessDeniedException("You do not have permission to edit GST OUT records.");
            }
        }
        return ResponseEntity.ok(gstOutRecordService.save(record));
    }

    @DeleteMapping("/gst-out/{id}")
    @RequiresPermission(module = Modules.GST_REPORT, action = "delete_gst_out")
    @Operation(summary = "Delete a manual GST OUT record")
    public ResponseEntity<Void> deleteGstOutRecord(@PathVariable String id) {
        gstOutRecordService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // GST IN Endpoint (Persisted)
    @GetMapping("/gst-in")
    @Operation(summary = "Generate or load monthly GST IN report from Client Entry records")
    public ResponseEntity<GstInReport> getGstInReport(
            @RequestParam String month,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        
        String monthUpper = month.trim().toUpperCase();
        var existingReport = gstInReportRepository.findByMonthIgnoreCaseAndYear(monthUpper, year);
        
        if (regenerate) {
            if (!permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "regenerate_gst_in")) {
                throw new AccessDeniedException("You do not have permission to regenerate GST Report.");
            }
        } else {
            if (existingReport.isPresent()) {
                if (!permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "view_gst_in")) {
                    throw new AccessDeniedException("You do not have permission to view GST IN.");
                }
            } else {
                if (!permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "generate_gst_in")) {
                    throw new AccessDeniedException("You do not have permission to generate GST Report.");
                }
            }
        }

        // If not regenerating, try to fetch from DB
        if (!regenerate && existingReport.isPresent()) {
            return ResponseEntity.ok(existingReport.get());
        }
        
        // Generate new report records
        var pageResponse = quotationService.search(
                null, null, null, null, null, null, monthUpper, year,
                null, null, true, null, null, null, null, null, null, null, null, 0, 10000);
        
        List<MonthlyCourierQuotationDto> quotations = pageResponse.getContent();
        List<GstInReport.GstInDetailsRecord> records = new ArrayList<>();
        
        int serialNo = 1;
        for (var q : quotations) {
            // Load entries for calculations
            List<MonthlyCourierEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(q.getId());
            var ctx = MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                    q.getCustomerId(),
                    q.getFuelChargePercentage(),
                    q.getFovCharges(),
                    q.getGstPercentage(),
                    q.getIncludeFuel(),
                    q.getIncludeGst(),
                    q.getIncludeFov()
            );
            
            var totals = invoiceGrandTotalService.computePdfTotals(ctx, entries);
            
            // Billing Amount = base + fuel + fov (which is totals.subTotal())
            double billingAmount = totals.subTotal();
            double cgst = totals.cgst();
            double sgst = totals.sgst();
            double gstTotal = cgst + sgst; // FIXED: GST Total = CGST + SGST
            
            // Round to 2 decimal places
            billingAmount = Math.round(billingAmount * 100.0) / 100.0;
            cgst = Math.round(cgst * 100.0) / 100.0;
            sgst = Math.round(sgst * 100.0) / 100.0;
            gstTotal = Math.round(gstTotal * 100.0) / 100.0;
            
            records.add(new GstInReport.GstInDetailsRecord(
                    serialNo++,
                    q.getCustomerName(),
                    billingAmount,
                    cgst,
                    sgst,
                    gstTotal
            ));
        }
        
        // Find existing to update or build new
        var reportOpt = gstInReportRepository.findByMonthIgnoreCaseAndYear(monthUpper, year);
        GstInReport report;
        if (reportOpt.isPresent()) {
            report = reportOpt.get();
            report.setRecords(records);
        } else {
            report = GstInReport.builder()
                    .month(monthUpper)
                    .year(year)
                    .records(records)
                    .build();
        }
        
        return ResponseEntity.ok(gstInReportRepository.save(report));
    }

    // Consolidated Ledger Endpoint
    @GetMapping("/ledger")
    @RequiresPermission(module = Modules.GST_REPORT, action = "view_ledger")
    @Operation(summary = "Get consolidated Monthly GST Ledger")
    public ResponseEntity<List<GstLedgerDto>> getGstLedger() {
        List<GstInReport> inReports = gstInReportRepository.findAll();
        List<GstOutRecord> outRecords = gstOutRecordService.findAll();
        List<GstLedgerDto> ledger = new ArrayList<>();
        
        // Identify all unique month-year periods
        java.util.Set<String> periods = new java.util.HashSet<>();
        for (var inRep : inReports) {
            periods.add(inRep.getMonth().toUpperCase() + "_" + inRep.getYear());
        }
        for (var outRec : outRecords) {
            if (outRec.getMonth() != null && outRec.getYear() != null) {
                periods.add(outRec.getMonth().toUpperCase() + "_" + outRec.getYear());
            }
        }
        
        for (String period : periods) {
            String[] parts = period.split("_");
            String month = parts[0];
            Integer year = Integer.parseInt(parts[1]);
            
            // Calculate GST IN Total for this period
            double gstInTotal = inReports.stream()
                    .filter(r -> r.getMonth().equalsIgnoreCase(month) && r.getYear().equals(year))
                    .findFirst()
                    .map(r -> r.getRecords().stream().mapToDouble(GstInReport.GstInDetailsRecord::getGstTotal).sum())
                    .orElse(0.0);
            
            // Calculate GST OUT Total for this period
            double gstOutTotal = outRecords.stream()
                    .filter(r -> month.equalsIgnoreCase(r.getMonth()) && year.equals(r.getYear()))
                    .mapToDouble(r -> r.getGstTotal() != null ? r.getGstTotal() : 0.0)
                    .sum();
            
            double diff = Math.abs(gstInTotal - gstOutTotal);
            String status = gstInTotal >= gstOutTotal ? "GST Payable" : "GST Receivable";
            
            gstInTotal = Math.round(gstInTotal * 100.0) / 100.0;
            gstOutTotal = Math.round(gstOutTotal * 100.0) / 100.0;
            diff = Math.round(diff * 100.0) / 100.0;
            
            ledger.add(new GstLedgerDto(month, year, gstInTotal, gstOutTotal, diff, status));
        }
        
        // Sort ledger by Year descending, then Month descending
        List<String> monthOrder = List.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");
        ledger.sort((a, b) -> {
            int yrCompare = b.getYear().compareTo(a.getYear());
            if (yrCompare != 0) return yrCompare;
            int aMonthIdx = monthOrder.indexOf(a.getMonth().toUpperCase());
            int bMonthIdx = monthOrder.indexOf(b.getMonth().toUpperCase());
            return Integer.compare(bMonthIdx, aMonthIdx);
        });
        
        return ResponseEntity.ok(ledger);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class GstLedgerDto {
        private String month;
        private Integer year;
        private double gstInTotal;
        private double gstOutTotal;
        private double difference;
        private String status;
    }
}

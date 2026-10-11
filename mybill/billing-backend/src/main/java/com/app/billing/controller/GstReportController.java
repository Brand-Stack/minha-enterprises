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
import com.app.billing.model.MonthlyCourierQuotation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
    private final MongoTemplate mongoTemplate;

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
    @Operation(summary = "Load monthly GST IN report from saved Client Entry records")
    public ResponseEntity<?> getGstInReport(
            @RequestParam String month,
            @RequestParam Integer year) {
        
        // VIEW ONLY: Load existing report if available without generating/saving empty records
        if (!permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "view_gst_in")) {
            throw new AccessDeniedException("You do not have permission to view GST IN.");
        }

        String monthUpper = month.trim().toUpperCase(Locale.ROOT);
        var existingReport = gstInReportRepository.findByMonthIgnoreCaseAndYear(monthUpper, year);
        if (existingReport.isPresent() && existingReport.get().getRecords() != null && !existingReport.get().getRecords().isEmpty()) {
            return ResponseEntity.ok(existingReport.get());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/gst-in/generate")
    @Operation(summary = "Generate monthly GST IN report from Client Entry records")
    public ResponseEntity<?> generateGstIn(
            @RequestParam String month,
            @RequestParam Integer year) {
        return executeGenerateGstIn(month, year);
    }

    private ResponseEntity<?> executeGenerateGstIn(String month, Integer year) {
        String monthUpper = month.trim().toUpperCase(Locale.ROOT);
        var existingReport = gstInReportRepository.findByMonthIgnoreCaseAndYear(monthUpper, year);

        boolean hasGenerate = permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "generate_gst_in");
        boolean hasRegenerate = permissionEvaluatorService.hasPermission(Modules.GST_REPORT, "regenerate_gst_in");
        if (!hasGenerate && !hasRegenerate) {
            throw new AccessDeniedException("You do not have permission to generate GST Report.");
        }

        List<MonthlyCourierQuotation> quotations = findClientEntryQuotations(monthUpper, year);
        List<GstInReport.GstInDetailsRecord> records = new ArrayList<>();

        int serialNo = 1;
        for (var q : quotations) {
            List<MonthlyCourierEntry> entries = entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc(q.getId());
            var ctx = MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                    q.getCustomerId(),
                    q.getFuelChargePercentage(),
                    q.getFovCharges(),
                    q.getGstPercentage(),
                    q.getIncludeFuel(),
                    q.getIncludeGst(),
                    q.getIncludeFov(),
                    q.getDiscountType(),
                    q.getDiscountValue(),
                    q.getAdditionalCharges()
            );

            var totals = invoiceGrandTotalService.computePdfTotals(ctx, entries);

            double billingAmount = totals.taxableAmount();
            double cgst = totals.cgst();
            double sgst = totals.sgst();
            double gstTotal = cgst + sgst;

            billingAmount = Math.round(billingAmount * 100.0) / 100.0;
            cgst = Math.round(cgst * 100.0) / 100.0;
            sgst = Math.round(sgst * 100.0) / 100.0;
            gstTotal = Math.round(gstTotal * 100.0) / 100.0;

            // Filter out empty quotation drafts with no shipments and 0 billing amount
            if (entries.isEmpty() && billingAmount <= 0.0 && gstTotal <= 0.0) {
                continue;
            }

            records.add(new GstInReport.GstInDetailsRecord(
                    serialNo++,
                    q.getCustomerName() != null ? q.getCustomerName() : "Unknown Client",
                    billingAmount,
                    cgst,
                    sgst,
                    gstTotal
            ));
        }

        if (records.isEmpty()) {
            // Clean up any empty report from previous failed generations so it doesn't corrupt database
            existingReport.ifPresent(gstInReportRepository::delete);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "No Client Entry billing records found for the selected month and year.")
            );
        }

        GstInReport report;
        if (existingReport.isPresent()) {
            report = existingReport.get();
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

    private static final List<String> MONTH_NAMES = List.of(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    private static int resolveMonthIndex(String month) {
        if (month == null || month.isBlank()) {
            return 0;
        }
        String m = month.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < MONTH_NAMES.size(); i++) {
            if (MONTH_NAMES.get(i).equals(m) || MONTH_NAMES.get(i).startsWith(m)) {
                return i + 1;
            }
        }
        try {
            int num = Integer.parseInt(m);
            if (num >= 1 && num <= 12) {
                return num;
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    private List<MonthlyCourierQuotation> findClientEntryQuotations(String monthUpper, Integer year) {
        int monthIdx = resolveMonthIndex(monthUpper);
        if (monthIdx < 1 || monthIdx > 12 || year == null) {
            return List.of();
        }

        java.time.YearMonth ym = java.time.YearMonth.of(year, monthIdx);
        java.time.LocalDate start = ym.atDay(1);
        java.time.LocalDate end = ym.atEndOfMonth();

        // Filter strictly by invoiceDate within the selected month and year
        Criteria periodCriteria = Criteria.where("invoiceDate").gte(start).lte(end);
        Criteria shopCriteria = Criteria.where("shopId").in("DEFAULT_SHOP", null);
        Criteria invoiceCriteria = new Criteria().orOperator(
                Criteria.where("invoiceGenerated").is(true),
                new Criteria().andOperator(
                        Criteria.where("invoiceGenerated").ne(false),
                        Criteria.where("invoiceNumber").exists(true).ne(null).ne("")
                )
        );
        Criteria excludeCriteria = Criteria.where("excludedFromReport").ne(true);

        Query query = new Query(new Criteria().andOperator(periodCriteria, shopCriteria, invoiceCriteria, excludeCriteria));

        // Stable order by createdAt
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdAt"));

        return mongoTemplate.find(query, MonthlyCourierQuotation.class);
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
            if (inRep.getMonth() != null && inRep.getYear() != null && inRep.getRecords() != null && !inRep.getRecords().isEmpty()) {
                periods.add(inRep.getMonth().toUpperCase(Locale.ROOT) + "_" + inRep.getYear());
            }
        }
        for (var outRec : outRecords) {
            if (outRec.getMonth() != null && outRec.getYear() != null) {
                periods.add(outRec.getMonth().toUpperCase(Locale.ROOT) + "_" + outRec.getYear());
            }
        }
        
        for (String period : periods) {
            String[] parts = period.split("_");
            String month = parts[0];
            Integer year = Integer.parseInt(parts[1]);
            
            // Calculate GST IN Total for this period
            double gstInTotal = inReports.stream()
                    .filter(r -> r.getMonth() != null && r.getMonth().equalsIgnoreCase(month) && r.getYear() != null && r.getYear().equals(year))
                    .findFirst()
                    .map(r -> r.getRecords() != null ? r.getRecords().stream().mapToDouble(GstInReport.GstInDetailsRecord::getGstTotal).sum() : 0.0)
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

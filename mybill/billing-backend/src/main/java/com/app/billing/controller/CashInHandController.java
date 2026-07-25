package com.app.billing.controller;

import com.app.billing.dto.CashInHandDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CashInHandService;
import com.app.billing.service.ReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/cash-in-hand")
@RequiredArgsConstructor
@RequiresPermission(module = Modules.CASH_IN_HAND, action = Modules.VIEW)
public class CashInHandController {
    
    private final CashInHandService cashInHandService;
    private final ReportPdfService reportPdfService;
    
    /**
     * Get Cash In Hand summary with filtering options
     * 
     * @param startDate Optional start date (YYYY-MM-DD)
     * @param endDate Optional end date (YYYY-MM-DD)
     * @param filterType Optional filter type: DAY, WEEK, MONTH, YEAR, CUSTOM
     * @return Cash In Hand summary with sales and expense entries
     */
    @GetMapping
    public ResponseEntity<CashInHandDto> getCashInHandSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String filterType) {
        
        CashInHandDto summary = cashInHandService.getCashInHandSummary(startDate, endDate, filterType);
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get sales drill-down entries
     * Shows all individual sales entries contributing to total sales
     * 
     * @param startDate Optional start date
     * @param endDate Optional end date
     * @param filterType Optional filter type
     * @return List of sales entries
     */
    @GetMapping("/sales")
    public ResponseEntity<List<CashInHandDto.SalesEntry>> getSalesDrillDown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String filterType) {
        
        List<CashInHandDto.SalesEntry> salesEntries = cashInHandService.getSalesDrillDown(startDate, endDate, filterType);
        return ResponseEntity.ok(salesEntries);
    }
    
    /**
     * Get expenses drill-down entries
     * Shows all individual expense entries contributing to total expenses
     * 
     * @param startDate Optional start date
     * @param endDate Optional end date
     * @param filterType Optional filter type
     * @return List of expense entries
     */
    @GetMapping("/expenses")
    public ResponseEntity<List<CashInHandDto.ExpenseEntry>> getExpensesDrillDown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String filterType) {
        
        List<CashInHandDto.ExpenseEntry> expenseEntries = cashInHandService.getExpensesDrillDown(startDate, endDate, filterType);
        return ResponseEntity.ok(expenseEntries);
    }
    
    /**
     * Get quick filter summaries for dashboard
     */
    @GetMapping("/quick-summary")
    public ResponseEntity<QuickSummaryResponse> getQuickSummary() {
        CashInHandDto today = cashInHandService.getCashInHandSummary(null, null, "DAY");
        CashInHandDto week = cashInHandService.getCashInHandSummary(null, null, "WEEK");
        CashInHandDto month = cashInHandService.getCashInHandSummary(null, null, "MONTH");
        CashInHandDto year = cashInHandService.getCashInHandSummary(null, null, "YEAR");
        
        return ResponseEntity.ok(new QuickSummaryResponse(today, week, month, year));
    }
    
    /**
     * Export Cash In Hand report as PDF
     */
    @GetMapping("/pdf")
    @RequiresPermission(module = Modules.CASH_IN_HAND, action = Modules.EXPORT)
    public ResponseEntity<byte[]> exportCashInHandPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String filterType) {
        
        CashInHandDto data = cashInHandService.getCashInHandSummary(startDate, endDate, filterType);
        byte[] pdfBytes = reportPdfService.generateCashInHandReport(data);
        
        String filename = "cash_in_hand_report_" + 
                data.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_to_" + 
                data.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    
    /**
     * Export Sales drill-down as PDF
     */
    @GetMapping("/sales/pdf")
    @RequiresPermission(module = Modules.CASH_IN_HAND, action = Modules.EXPORT)
    public ResponseEntity<byte[]> exportSalesDrillDownPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String filterType) {
        
        CashInHandDto data = cashInHandService.getCashInHandSummary(startDate, endDate, filterType);
        // Only include sales entries
        data.setExpenseEntries(null);
        byte[] pdfBytes = reportPdfService.generateCashInHandReport(data);
        
        String filename = "sales_drill_down_" + 
                data.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_to_" + 
                data.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    
    /**
     * Export Expenses drill-down as PDF
     */
    @GetMapping("/expenses/pdf")
    @RequiresPermission(module = Modules.CASH_IN_HAND, action = Modules.EXPORT)
    public ResponseEntity<byte[]> exportExpensesDrillDownPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String filterType) {
        
        CashInHandDto data = cashInHandService.getCashInHandSummary(startDate, endDate, filterType);
        // Only include expense entries
        data.setSalesEntries(null);
        byte[] pdfBytes = reportPdfService.generateCashInHandReport(data);
        
        String filename = "expenses_drill_down_" + 
                data.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_to_" + 
                data.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class QuickSummaryResponse {
        private CashInHandDto today;
        private CashInHandDto thisWeek;
        private CashInHandDto thisMonth;
        private CashInHandDto thisYear;
    }
}

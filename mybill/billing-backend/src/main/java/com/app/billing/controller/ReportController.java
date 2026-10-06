package com.app.billing.controller;

import com.app.billing.dto.DashboardDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.DashboardService;
import com.app.billing.service.ReportExcelService;
import com.app.billing.service.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Generate and export reports")
@RequiresPermission(module = Modules.REPORTS, action = Modules.VIEW)
public class ReportController {
    
    private final DashboardService dashboardService;
    private final ReportPdfService reportPdfService;
    private final ReportExcelService reportExcelService;
    
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    
    @GetMapping("/billing/pdf")
    @Operation(summary = "Export transaction report as PDF")
    public ResponseEntity<byte[]> exportBillingReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") String groupBy,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String invoiceNumber) {
        
        try {
            List<DashboardDto.BillingData> data = dashboardService.getBillingDataWithFilters(startDate, endDate, billType, partyId, invoiceNumber);
            byte[] pdfBytes = reportPdfService.generateBillingReport(data, startDate, endDate, billType);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String filename = "transaction_report_" + startDate + "_to_" + endDate;
            if (billType != null && !billType.isEmpty()) {
                filename += "_" + billType;
            }
            headers.setContentDispositionFormData("attachment", filename + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating transaction report PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/purchase/pdf")
    @Operation(summary = "Export purchase report as PDF")
    public ResponseEntity<byte[]> exportPurchaseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String item) {
        
        try {
            List<DashboardDto.PurchaseData> data = dashboardService.getPurchaseData(startDate, endDate, item, category);
            byte[] pdfBytes = reportPdfService.generatePurchaseReport(data, startDate, endDate);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "purchase_report_" + startDate + "_to_" + endDate + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating purchase report PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/party/pdf")
    @Operation(summary = "Export party report as PDF")
    public ResponseEntity<byte[]> exportPartyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String billType) {
        
        try {
            List<DashboardDto.PartyData> data = dashboardService.getPartyData(startDate, endDate, billType);
            byte[] pdfBytes = reportPdfService.generatePartyReport(data, startDate, endDate);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "party_report_" + startDate + "_to_" + endDate + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating party report PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/stock/pdf")
    @Operation(summary = "Export stock report as PDF")
    public ResponseEntity<byte[]> exportStockReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        try {
            List<DashboardDto.StockData> data = dashboardService.getStockDataWithFilters(category, search);
            byte[] pdfBytes = reportPdfService.generateStockReport(data);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "stock_report_" + LocalDate.now() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating stock report PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    // REMOVED: Sales Report endpoints - module deprecated
    
    @GetMapping("/cash-in-hand")
    @Operation(summary = "Get Cash In Hand report", description = "Total Sales (Day) - Total Expenses (Day). Shows yesterday's value by default.")
    public ResponseEntity<DashboardDto.CashInHandData> getCashInHand(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dashboardService.getCashInHandData(date));
    }
    
    @GetMapping("/purchase")
    @Operation(summary = "Get Purchase Report", description = "Show only Supplier purchases with filters")
    public ResponseEntity<List<DashboardDto.PurchaseData>> getPurchaseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String item) {
        return ResponseEntity.ok(dashboardService.getPurchaseData(startDate, endDate, item, category));
    }
    
    // REMOVED: GET /sales endpoint - module deprecated (see Transaction Report instead)
    
    @GetMapping("/billing")
    @Operation(summary = "Get Transaction Report", description = "Transaction report with filters")
    public ResponseEntity<List<DashboardDto.BillingData>> getBillingReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String invoiceNumber) {
        return ResponseEntity.ok(dashboardService.getBillingDataWithFilters(startDate, endDate, billType, partyId, invoiceNumber));
    }
    
    @GetMapping("/stock")
    @Operation(summary = "Get Stock Report", description = "Stock report with category filter and search")
    public ResponseEntity<List<DashboardDto.StockData>> getStockReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(dashboardService.getStockDataWithFilters(category, search));
    }
    
    @GetMapping("/expense")
    @Operation(summary = "Get Expense Report", description = "Expense report with date range and category filter")
    public ResponseEntity<List<DashboardDto.ExpenseData>> getExpenseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(dashboardService.getExpenseData(startDate, endDate, category));
    }
    
    @GetMapping("/expense/pdf")
    @Operation(summary = "Export expense report as PDF")
    public ResponseEntity<byte[]> exportExpenseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category) {
        
        try {
            List<DashboardDto.ExpenseData> data = dashboardService.getExpenseData(startDate, endDate, category);
            byte[] pdfBytes = reportPdfService.generateExpenseReport(data, startDate, endDate);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "expense_report_" + startDate + "_to_" + endDate + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating expense report PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    // ================================
    // EXCEL EXPORT ENDPOINTS
    // ================================
    
    @GetMapping("/billing/excel")
    @Operation(summary = "Export transaction report as Excel (invoice-level detail)")
    public ResponseEntity<byte[]> exportBillingReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") String groupBy,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String invoiceNumber) {
        
        try {
            List<DashboardDto.TransactionReportRow> data = dashboardService.getTransactionReportDetailRows(startDate, endDate, billType, partyId, invoiceNumber);
            byte[] excelBytes = reportExcelService.generateTransactionReportDetailExcel(data, startDate, endDate, billType);
            
            String filename = "transaction_report_" + startDate + "_to_" + endDate;
            if (billType != null && !billType.isEmpty()) {
                filename += "_" + billType;
            }
            
            return createExcelResponse(excelBytes, filename + ".xlsx");
        } catch (Exception e) {
            log.error("Error generating transaction report Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/purchase/excel")
    @Operation(summary = "Export purchase report as Excel")
    public ResponseEntity<byte[]> exportPurchaseReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String item) {
        
        try {
            List<DashboardDto.PurchaseData> data = dashboardService.getPurchaseData(startDate, endDate, item, category);
            byte[] excelBytes = reportExcelService.generatePurchaseReportExcel(data, startDate, endDate);
            
            return createExcelResponse(excelBytes, "purchase_report_" + startDate + "_to_" + endDate + ".xlsx");
        } catch (Exception e) {
            log.error("Error generating purchase report Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/party/excel")
    @Operation(summary = "Export party report as Excel")
    public ResponseEntity<byte[]> exportPartyReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String billType) {
        
        try {
            List<DashboardDto.PartyData> data = dashboardService.getPartyData(startDate, endDate, billType);
            byte[] excelBytes = reportExcelService.generatePartyReportExcel(data, startDate, endDate);
            
            return createExcelResponse(excelBytes, "party_report_" + startDate + "_to_" + endDate + ".xlsx");
        } catch (Exception e) {
            log.error("Error generating party report Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/stock/excel")
    @Operation(summary = "Export stock report as Excel")
    public ResponseEntity<byte[]> exportStockReportExcel(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        try {
            List<DashboardDto.StockData> data = dashboardService.getStockDataWithFilters(category, search);
            byte[] excelBytes = reportExcelService.generateStockReportExcel(data);
            
            return createExcelResponse(excelBytes, "stock_report_" + LocalDate.now() + ".xlsx");
        } catch (Exception e) {
            log.error("Error generating stock report Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/expense/excel")
    @Operation(summary = "Export expense report as Excel")
    public ResponseEntity<byte[]> exportExpenseReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category) {
        
        try {
            List<DashboardDto.ExpenseData> data = dashboardService.getExpenseData(startDate, endDate, category);
            byte[] excelBytes = reportExcelService.generateExpenseReportExcel(data, startDate, endDate);
            
            return createExcelResponse(excelBytes, "expense_report_" + startDate + "_to_" + endDate + ".xlsx");
        } catch (Exception e) {
            log.error("Error generating expense report Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    @GetMapping("/cash-in-hand/excel")
    @Operation(summary = "Export cash in hand report as Excel")
    public ResponseEntity<byte[]> exportCashInHandReportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            DashboardDto.CashInHandData data = dashboardService.getCashInHandData(date);
            byte[] excelBytes = reportExcelService.generateCashInHandReportExcel(data, date);
            
            String reportDate = date != null ? date.toString() : LocalDate.now().toString();
            return createExcelResponse(excelBytes, "cash_in_hand_" + reportDate + ".xlsx");
        } catch (Exception e) {
            log.error("Error generating cash in hand report Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
    
    // Helper method for Excel responses
    private ResponseEntity<byte[]> createExcelResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        headers.add("Access-Control-Expose-Headers", "Content-Disposition");
        
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(data.length)
                .body(data);
    }
}


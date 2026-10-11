package com.app.billing.service;

import com.app.billing.dto.DashboardDto;
import com.app.billing.dto.InvoiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating Excel reports.
 * Uses SXSSFWorkbook for memory-efficient streaming of large datasets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExcelService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String CURRENCY_FORMAT = "₹#,##0.00";

    // ========================
    // TRANSACTION LISTING EXCEL (Invoice module export)
    // ========================
    public byte[] generateInvoiceListExcel(List<InvoiceDto> data) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Transactions");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            int rowNum = 0;
            
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Transaction List - Exported " + LocalDate.now().format(DATE_FORMATTER));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
            rowNum++;
            
            String[] headers = {"Date", "Invoice No", "Party", "Type", "Status", "Total Amount", "Received", "Pending", "Payment Mode"};
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal grandReceived = BigDecimal.ZERO;
            
            for (InvoiceDto inv : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DATE_FORMATTER) : "");
                row.createCell(1).setCellValue(inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "");
                row.createCell(2).setCellValue(inv.getPartyName() != null ? inv.getPartyName() : "");
                row.createCell(3).setCellValue(inv.getBillType() != null ? inv.getBillType().name() : "");
                row.createCell(4).setCellValue(inv.getStatus() != null ? inv.getStatus().name() : "");
                
                BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal received = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
                if (received.compareTo(BigDecimal.ZERO) == 0 && inv.getReceivedCashAmount() != null && inv.getReceivedOnlineAmount() != null) {
                    received = inv.getReceivedCashAmount().add(inv.getReceivedOnlineAmount());
                }
                BigDecimal pending = total.subtract(received);
                
                Cell totalCell = row.createCell(5);
                totalCell.setCellValue(total.doubleValue());
                totalCell.setCellStyle(currencyStyle);
                Cell recCell = row.createCell(6);
                recCell.setCellValue(received.doubleValue());
                recCell.setCellStyle(currencyStyle);
                Cell pendCell = row.createCell(7);
                pendCell.setCellValue(pending.doubleValue());
                pendCell.setCellStyle(currencyStyle);
                row.createCell(8).setCellValue(inv.getModeOfPayment() != null ? inv.getModeOfPayment().name() : "");
                
                grandTotal = grandTotal.add(total);
                grandReceived = grandReceived.add(received);
            }
            
            rowNum++;
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTAL");
            Cell totalSumCell = summaryRow.createCell(5);
            totalSumCell.setCellValue(grandTotal.doubleValue());
            totalSumCell.setCellStyle(currencyStyle);
            Cell recSumCell = summaryRow.createCell(6);
            recSumCell.setCellValue(grandReceived.doubleValue());
            recSumCell.setCellStyle(currencyStyle);
            
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }
            
            return writeToByteArray(workbook);
        }
    }

    // ========================
    // TRANSACTION REPORT EXCEL (Invoice-level detail)
    // ========================
    public byte[] generateTransactionReportDetailExcel(List<DashboardDto.TransactionReportRow> data,
                                                       LocalDate startDate, LocalDate endDate,
                                                       String billType) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Transaction Report");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            int rowNum = 0;
            
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Transaction Report: " + startDate.format(DATE_FORMATTER) + " to " + endDate.format(DATE_FORMATTER) +
                    (billType != null && !billType.isEmpty() ? " (" + billType + ")" : ""));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
            rowNum++;
            
            String[] headers = {"Date", "Bill Number", "Party Name", "Type", "Total Amount", "Received Amount", "Pending Amount", "Status", "Payment Mode"};
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal grandReceived = BigDecimal.ZERO;
            BigDecimal grandPending = BigDecimal.ZERO;
            
            for (DashboardDto.TransactionReportRow item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getDate() != null ? item.getDate().format(DATE_FORMATTER) : "");
                row.createCell(1).setCellValue(item.getBillNumber() != null ? item.getBillNumber() : "");
                row.createCell(2).setCellValue(item.getPartyName() != null ? item.getPartyName() : "");
                row.createCell(3).setCellValue(item.getBillType() != null ? item.getBillType() : "");
                
                Cell totalCell = row.createCell(4);
                totalCell.setCellValue(item.getTotalAmount() != null ? item.getTotalAmount().doubleValue() : 0);
                totalCell.setCellStyle(currencyStyle);
                
                Cell receivedCell = row.createCell(5);
                receivedCell.setCellValue(item.getReceivedAmount() != null ? item.getReceivedAmount().doubleValue() : 0);
                receivedCell.setCellStyle(currencyStyle);
                
                Cell pendingCell = row.createCell(6);
                pendingCell.setCellValue(item.getPendingAmount() != null ? item.getPendingAmount().doubleValue() : 0);
                pendingCell.setCellStyle(currencyStyle);
                
                row.createCell(7).setCellValue(item.getPaymentStatus() != null ? item.getPaymentStatus() : "");
                row.createCell(8).setCellValue(item.getPaymentMode() != null ? item.getPaymentMode() : "");
                
                if (item.getTotalAmount() != null) grandTotal = grandTotal.add(item.getTotalAmount());
                if (item.getReceivedAmount() != null) grandReceived = grandReceived.add(item.getReceivedAmount());
                if (item.getPendingAmount() != null) grandPending = grandPending.add(item.getPendingAmount());
            }
            
            rowNum++;
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTAL");
            Cell totalSumCell = summaryRow.createCell(4);
            totalSumCell.setCellValue(grandTotal.doubleValue());
            totalSumCell.setCellStyle(currencyStyle);
            Cell receivedSumCell = summaryRow.createCell(5);
            receivedSumCell.setCellValue(grandReceived.doubleValue());
            receivedSumCell.setCellStyle(currencyStyle);
            Cell pendingSumCell = summaryRow.createCell(6);
            pendingSumCell.setCellValue(grandPending.doubleValue());
            pendingSumCell.setCellStyle(currencyStyle);
            
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }
            
            return writeToByteArray(workbook);
        }
    }
    
    /** Aggregated Transaction Report Excel (by date) - kept for backward compatibility */
    public byte[] generateTransactionReportExcel(List<DashboardDto.BillingData> data, 
                                                  LocalDate startDate, LocalDate endDate, 
                                                  String billType) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Transaction Report");
            
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            
            int rowNum = 0;
            
            // Title row
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Transaction Report: " + startDate.format(DATE_FORMATTER) + 
                                   " to " + endDate.format(DATE_FORMATTER) +
                                   (billType != null ? " (" + billType + ")" : ""));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            
            rowNum++; // Empty row
            
            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Date", "Total Invoices", "GST Bills", "Estimate Bills", "GST Amount", "Estimate Amount", "Total Amount"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal gstTotal = BigDecimal.ZERO;
            BigDecimal estimateTotal = BigDecimal.ZERO;
            long totalInvoices = 0;
            
            for (DashboardDto.BillingData item : data) {
                Row row = sheet.createRow(rowNum++);
                
                Cell dateCell = row.createCell(0);
                if (item.getDate() != null) {
                    dateCell.setCellValue(item.getDate().format(DATE_FORMATTER));
                } else {
                    dateCell.setCellValue(item.getPeriod());
                }
                
                row.createCell(1).setCellValue(item.getTotalInvoices() != null ? item.getTotalInvoices() : 0);
                row.createCell(2).setCellValue(item.getGstBillCount() != null ? item.getGstBillCount() : 0);
                row.createCell(3).setCellValue(item.getEstimateCount() != null ? item.getEstimateCount() : 0);
                
                Cell gstCell = row.createCell(4);
                gstCell.setCellValue(item.getGstAmount() != null ? item.getGstAmount().doubleValue() : 0);
                gstCell.setCellStyle(currencyStyle);
                
                Cell estCell = row.createCell(5);
                estCell.setCellValue(item.getEstimateAmount() != null ? item.getEstimateAmount().doubleValue() : 0);
                estCell.setCellStyle(currencyStyle);
                
                Cell totalCell = row.createCell(6);
                totalCell.setCellValue(item.getTotalAmount() != null ? item.getTotalAmount().doubleValue() : 0);
                totalCell.setCellStyle(currencyStyle);
                
                // Accumulate totals
                if (item.getTotalAmount() != null) grandTotal = grandTotal.add(item.getTotalAmount());
                if (item.getGstAmount() != null) gstTotal = gstTotal.add(item.getGstAmount());
                if (item.getEstimateAmount() != null) estimateTotal = estimateTotal.add(item.getEstimateAmount());
                if (item.getTotalInvoices() != null) totalInvoices += item.getTotalInvoices();
            }
            
            // Summary row
            rowNum++; // Empty row
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTAL");
            summaryRow.createCell(1).setCellValue(totalInvoices);
            
            Cell gstTotalCell = summaryRow.createCell(4);
            gstTotalCell.setCellValue(gstTotal.doubleValue());
            gstTotalCell.setCellStyle(currencyStyle);
            
            Cell estTotalCell = summaryRow.createCell(5);
            estTotalCell.setCellValue(estimateTotal.doubleValue());
            estTotalCell.setCellStyle(currencyStyle);
            
            Cell grandTotalCell = summaryRow.createCell(6);
            grandTotalCell.setCellValue(grandTotal.doubleValue());
            grandTotalCell.setCellStyle(currencyStyle);
            
            // Auto-size columns (note: limited support in SXSSFWorkbook)
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }
            
            return writeToByteArray(workbook);
        }
    }

    // ========================
    // PURCHASE REPORT EXCEL
    // ========================
    public byte[] generatePurchaseReportExcel(List<DashboardDto.PurchaseData> data,
                                               LocalDate startDate, LocalDate endDate) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Purchase Report");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            int rowNum = 0;
            
            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Purchase Report: " + startDate.format(DATE_FORMATTER) + 
                                   " to " + endDate.format(DATE_FORMATTER));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            
            rowNum++;
            
            // Header - PurchaseData is line-level: billNumber, billDate, partyName, itemCode, itemName, quantity, unitPrice, totalAmount, etc.
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Bill No", "Date", "Party", "Item Code", "Item Name", "Qty", "Unit Price", "Total", "Payment Type", "Status", "Paid", "Outstanding"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Data
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (DashboardDto.PurchaseData item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getBillNumber() != null ? item.getBillNumber() : "");
                row.createCell(1).setCellValue(item.getBillDate() != null ? item.getBillDate().format(DATE_FORMATTER) : "");
                row.createCell(2).setCellValue(item.getPartyName() != null ? item.getPartyName() : "");
                row.createCell(3).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(4).setCellValue(item.getItemName() != null ? item.getItemName() : "");
                row.createCell(5).setCellValue(item.getQuantity() != null ? item.getQuantity().doubleValue() : 0);
                Cell unitPriceCell = row.createCell(6);
                unitPriceCell.setCellValue(item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : 0);
                unitPriceCell.setCellStyle(currencyStyle);
                Cell totalCell = row.createCell(7);
                totalCell.setCellValue(item.getTotalAmount() != null ? item.getTotalAmount().doubleValue() : 0);
                totalCell.setCellStyle(currencyStyle);
                row.createCell(8).setCellValue(item.getPaymentType() != null ? item.getPaymentType() : "");
                row.createCell(9).setCellValue(item.getPaymentStatus() != null ? item.getPaymentStatus() : "");
                Cell paidCell = row.createCell(10);
                paidCell.setCellValue(item.getPaidAmount() != null ? item.getPaidAmount().doubleValue() : 0);
                paidCell.setCellStyle(currencyStyle);
                Cell outCell = row.createCell(11);
                outCell.setCellValue(item.getOutstandingAmount() != null ? item.getOutstandingAmount().doubleValue() : 0);
                outCell.setCellStyle(currencyStyle);
                
                if (item.getTotalAmount() != null) grandTotal = grandTotal.add(item.getTotalAmount());
            }
            
            // Summary
            rowNum++;
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTAL");
            Cell totalSumCell = summaryRow.createCell(7);
            totalSumCell.setCellValue(grandTotal.doubleValue());
            totalSumCell.setCellStyle(currencyStyle);
            
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }
            
            return writeToByteArray(workbook);
        }
    }

    // ========================
    // STOCK REPORT EXCEL
    // ========================
    public byte[] generateStockReportExcel(List<DashboardDto.StockData> data) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Stock Report");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            int rowNum = 0;
            
            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Stock Report - Generated: " + LocalDate.now().format(DATE_FORMATTER));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            
            rowNum++;
            
            // Header - StockData: itemCode, itemName, currentStock, minStockLevel, status, stockValue
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Item Code", "Item Name", "Current Stock", "Min Stock", "Status", "Stock Value"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Data
            BigDecimal totalValue = BigDecimal.ZERO;
            for (DashboardDto.StockData item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(1).setCellValue(item.getItemName() != null ? item.getItemName() : "");
                row.createCell(2).setCellValue(item.getCurrentStock() != null ? item.getCurrentStock().doubleValue() : 0);
                row.createCell(3).setCellValue(item.getMinStockLevel() != null ? item.getMinStockLevel().doubleValue() : 0);
                row.createCell(4).setCellValue(item.getStatus() != null ? item.getStatus() : "");
                
                Cell valueCell = row.createCell(5);
                valueCell.setCellValue(item.getStockValue() != null ? item.getStockValue().doubleValue() : 0);
                valueCell.setCellStyle(currencyStyle);
                
                if (item.getStockValue() != null) totalValue = totalValue.add(item.getStockValue());
            }
            
            // Summary
            rowNum++;
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTAL STOCK VALUE");
            Cell totalCell = summaryRow.createCell(5);
            totalCell.setCellValue(totalValue.doubleValue());
            totalCell.setCellStyle(currencyStyle);
            
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4500);
            }
            
            return writeToByteArray(workbook);
        }
    }

    // ========================
    // PARTY REPORT EXCEL
    // ========================
    public byte[] generatePartyReportExcel(List<DashboardDto.PartyData> data,
                                            LocalDate startDate, LocalDate endDate) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Party Report");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            int rowNum = 0;
            
            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Party Report: " + startDate.format(DATE_FORMATTER) + 
                                   " to " + endDate.format(DATE_FORMATTER));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
            
            rowNum++;
            
            // Header - PartyData: partyId, partyName, totalBills, gstBills, estimateBills, totalAmount, gstAmount, estimateAmount
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Party ID", "Party Name", "Total Bills", "GST Bills", "Estimate Bills", "Total Amount", "GST Amount", "Estimate Amount"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Data
            BigDecimal grandTotal = BigDecimal.ZERO;
            BigDecimal gstTotal = BigDecimal.ZERO;
            BigDecimal estimateTotal = BigDecimal.ZERO;
            
            for (DashboardDto.PartyData item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getPartyId() != null ? item.getPartyId() : "");
                row.createCell(1).setCellValue(item.getPartyName() != null ? item.getPartyName() : "");
                row.createCell(2).setCellValue(item.getTotalBills() != null ? item.getTotalBills() : 0);
                row.createCell(3).setCellValue(item.getGstBills() != null ? item.getGstBills() : 0);
                row.createCell(4).setCellValue(item.getEstimateBills() != null ? item.getEstimateBills() : 0);
                
                Cell amountCell = row.createCell(5);
                amountCell.setCellValue(item.getTotalAmount() != null ? item.getTotalAmount().doubleValue() : 0);
                amountCell.setCellStyle(currencyStyle);
                
                Cell gstCell = row.createCell(6);
                gstCell.setCellValue(item.getGstAmount() != null ? item.getGstAmount().doubleValue() : 0);
                gstCell.setCellStyle(currencyStyle);
                
                Cell estCell = row.createCell(7);
                estCell.setCellValue(item.getEstimateAmount() != null ? item.getEstimateAmount().doubleValue() : 0);
                estCell.setCellStyle(currencyStyle);
                
                if (item.getTotalAmount() != null) grandTotal = grandTotal.add(item.getTotalAmount());
                if (item.getGstAmount() != null) gstTotal = gstTotal.add(item.getGstAmount());
                if (item.getEstimateAmount() != null) estimateTotal = estimateTotal.add(item.getEstimateAmount());
            }
            
            // Summary
            rowNum++;
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTAL");
            
            Cell totalAmountCell = summaryRow.createCell(5);
            totalAmountCell.setCellValue(grandTotal.doubleValue());
            totalAmountCell.setCellStyle(currencyStyle);
            
            Cell totalGstCell = summaryRow.createCell(6);
            totalGstCell.setCellValue(gstTotal.doubleValue());
            totalGstCell.setCellStyle(currencyStyle);
            
            Cell totalEstCell = summaryRow.createCell(7);
            totalEstCell.setCellValue(estimateTotal.doubleValue());
            totalEstCell.setCellStyle(currencyStyle);
            
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4000);
            }
            
            return writeToByteArray(workbook);
        }
    }

    // ========================
    // EXPENSE REPORT EXCEL
    // ========================
    public byte[] generateExpenseReportExcel(List<DashboardDto.ExpenseData> data,
                                              LocalDate startDate, LocalDate endDate) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Cash In Report");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            int rowNum = 0;
            
            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Cash Ledger Report: " + startDate.format(DATE_FORMATTER) + 
                                   " to " + endDate.format(DATE_FORMATTER));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
            
            rowNum++;
            
            // Header
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Voucher No", "Date", "Type", "Category", "Party", "Payment Mode", "Cash In", "Cash Out", "Description"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Data
            BigDecimal totalIn = BigDecimal.ZERO;
            BigDecimal totalOut = BigDecimal.ZERO;
            for (DashboardDto.ExpenseData item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getExpenseNumber() != null ? item.getExpenseNumber() : "");
                row.createCell(1).setCellValue(item.getExpenseDate() != null ? item.getExpenseDate().format(DATE_FORMATTER) : "");
                
                boolean isOut = "CASH_OUT".equalsIgnoreCase(item.getTransactionType());
                row.createCell(2).setCellValue(isOut ? "Cash Out" : "Cash In");
                row.createCell(3).setCellValue(item.getCategory() != null ? item.getCategory() : "");
                row.createCell(4).setCellValue(item.getPartyName() != null ? item.getPartyName() : "");
                row.createCell(5).setCellValue(item.getPaymentMode() != null ? item.getPaymentMode() : "");
                
                BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                
                Cell cashInCell = row.createCell(6);
                cashInCell.setCellStyle(currencyStyle);
                Cell cashOutCell = row.createCell(7);
                cashOutCell.setCellStyle(currencyStyle);
                
                if (isOut) {
                    cashInCell.setCellValue(0.0);
                    cashOutCell.setCellValue(amount.doubleValue());
                    totalOut = totalOut.add(amount);
                } else {
                    cashInCell.setCellValue(amount.doubleValue());
                    cashOutCell.setCellValue(0.0);
                    totalIn = totalIn.add(amount);
                }
                
                row.createCell(8).setCellValue(item.getDescription() != null ? item.getDescription() : "");
            }
            
            // Summary
            rowNum++;
            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("TOTALS");
            
            Cell sumInCell = summaryRow.createCell(6);
            sumInCell.setCellValue(totalIn.doubleValue());
            sumInCell.setCellStyle(currencyStyle);
            
            Cell sumOutCell = summaryRow.createCell(7);
            sumOutCell.setCellValue(totalOut.doubleValue());
            sumOutCell.setCellStyle(currencyStyle);
            
            rowNum++;
            Row netRow = sheet.createRow(rowNum);
            netRow.createCell(0).setCellValue("NET CASH");
            Cell netCell = netRow.createCell(6);
            netCell.setCellValue(totalIn.subtract(totalOut).doubleValue());
            netCell.setCellStyle(currencyStyle);
            
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4500);
            }
            
            return writeToByteArray(workbook);
        }
    }

    // ========================
    // CASH IN HAND REPORT EXCEL
    // ========================
    public byte[] generateCashInHandReportExcel(DashboardDto.CashInHandData data, LocalDate date) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) { // Regular workbook for small data
            Sheet sheet = workbook.createSheet("Cash In Hand");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle labelStyle = createLabelStyle(workbook);
            
            int rowNum = 0;
            
            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Cash In Hand Report - " + (date != null ? date.format(DATE_FORMATTER) : LocalDate.now().format(DATE_FORMATTER)));
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
            
            rowNum += 2;
            
            // CashInHandData has: date, totalSales, totalExpenses, liquidCash, onlineBalance, totalAvailableBalance
            addCashInHandRow(sheet, rowNum++, "Total Sales", data.getTotalSales(), labelStyle, currencyStyle);
            addCashInHandRow(sheet, rowNum++, "Total Cash In", data.getTotalExpenses(), labelStyle, currencyStyle);
            rowNum++;
            
            addCashInHandRow(sheet, rowNum++, "Liquid Cash", data.getLiquidCash(), labelStyle, currencyStyle);
            addCashInHandRow(sheet, rowNum++, "Online Balance", data.getOnlineBalance(), labelStyle, currencyStyle);
            addCashInHandRow(sheet, rowNum++, "Total Available Balance", data.getTotalAvailableBalance(), labelStyle, currencyStyle);
            
            sheet.setColumnWidth(0, 8000);
            sheet.setColumnWidth(1, 5000);
            
            return writeToByteArray(workbook);
        }
    }
    
    private void addCashInHandRow(Sheet sheet, int rowNum, String label, BigDecimal value, 
                                   CellStyle labelStyle, CellStyle currencyStyle) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value.doubleValue() : 0);
        valueCell.setCellStyle(currencyStyle);
    }

    // ========================
    // STYLE HELPERS
    // ========================
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(CURRENCY_FORMAT));
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd-MM-yyyy"));
        return style;
    }

    private CellStyle createLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(false);
        style.setFont(font);
        return style;
    }

    private byte[] writeToByteArray(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}

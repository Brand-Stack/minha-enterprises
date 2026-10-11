package com.app.billing.service;

import com.app.billing.dao.ItemRepository;
import com.app.billing.dto.CashInHandDto;
import com.app.billing.dto.DashboardDto;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.Item;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service for generating professionally formatted PDF reports using iText 7.
 * Features:
 * - No text overlapping - all text wraps properly
 * - Dynamic row heights based on content
 * - Professional layout with proper pagination
 * - Consistent formatting across all report types
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPdfService {
    
    private final CompanySettingsService companySettingsService;
    private final ItemRepository itemRepository;
    
    // Colors
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(91, 111, 232);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(240, 240, 240);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(200, 200, 200);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb DARK_TEXT = new DeviceRgb(30, 30, 30);
    
    // Formatting
    private static final float PAGE_MARGIN = 36f;
    private static final float CELL_PADDING = 6f;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_LONG_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ==================== BILLING/TRANSACTION REPORT ====================
    
    public byte[] generateBillingReport(List<DashboardDto.BillingData> data, LocalDate startDate, LocalDate endDate, String billType) {
        log.info("Generating Billing Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            String title = "TRANSACTION REPORT";
            if (billType != null && !billType.isEmpty()) {
                title += " (" + billType + " Bills)";
            }
            addReportHeader(document, title, startDate, endDate, boldFont, regularFont);
            
            // Summary
            addBillingSummary(document, data, boldFont, regularFont);
            
            // Table
            addBillingTable(document, data, boldFont, regularFont);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Billing Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addBillingSummary(Document document, List<DashboardDto.BillingData> data, PdfFont boldFont, PdfFont regularFont) {
        long totalInvoices = data.stream().mapToLong(DashboardDto.BillingData::getTotalInvoices).sum();
        long estimateCount = data.stream().mapToLong(DashboardDto.BillingData::getEstimateCount).sum();
        long gstCount = data.stream().mapToLong(DashboardDto.BillingData::getGstBillCount).sum();
        BigDecimal totalAmount = data.stream()
                .map(DashboardDto.BillingData::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph summaryTitle = new Paragraph("Summary")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(10)
                .setMarginBottom(8);
        document.add(summaryTitle);
        
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        addSummaryCell(summaryTable, "Total Transactions", String.valueOf(totalInvoices), boldFont, regularFont);
        addSummaryCell(summaryTable, "Estimates", String.valueOf(estimateCount), boldFont, regularFont);
        addSummaryCell(summaryTable, "GST Bills", String.valueOf(gstCount), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Amount", "₹" + formatAmount(totalAmount), boldFont, regularFont);
        
        document.add(summaryTable);
    }
    
    private void addBillingTable(Document document, List<DashboardDto.BillingData> data, PdfFont boldFont, PdfFont regularFont) {
        // Column widths: Period, Total, Estimates, GST Bills, Amount
        Table table = new Table(UnitValue.createPercentArray(new float[]{2.5f, 1.5f, 1.5f, 1.5f, 2f}))
                .useAllAvailableWidth();
        
        // Headers
        String[] headers = {"Period", "Total Transactions", "Estimates", "GST Bills", "Amount (₹)"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows
        for (DashboardDto.BillingData row : data) {
            table.addCell(createDataCell(row.getPeriod() != null ? row.getPeriod() : formatDate(row.getDate()), regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(String.valueOf(row.getTotalInvoices()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(String.valueOf(row.getEstimateCount()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(String.valueOf(row.getGstBillCount()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(formatAmount(row.getTotalAmount()), regularFont, TextAlignment.RIGHT));
        }
        
        document.add(table);
    }
    
    // ==================== STOCK REPORT ====================
    
    public byte[] generateStockReport(List<DashboardDto.StockData> data) {
        log.info("Generating Stock Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "STOCK REPORT", LocalDate.now(), LocalDate.now(), boldFont, regularFont);
            
            // Summary
            addStockSummary(document, data, boldFont, regularFont);
            
            // Table
            addStockTable(document, data, boldFont, regularFont);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Stock Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addStockSummary(Document document, List<DashboardDto.StockData> data, PdfFont boldFont, PdfFont regularFont) {
        long okCount = data.stream().filter(d -> "OK".equals(d.getStatus())).count();
        long lowCount = data.stream().filter(d -> "LOW".equals(d.getStatus())).count();
        long outCount = data.stream().filter(d -> "OUT_OF_STOCK".equals(d.getStatus())).count();
        long negativeCount = data.stream().filter(d -> "NEGATIVE".equals(d.getStatus())).count();
        BigDecimal totalValue = data.stream()
                .map(DashboardDto.StockData::getStockValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph summaryTitle = new Paragraph("Summary")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(10)
                .setMarginBottom(8);
        document.add(summaryTitle);
        
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        addSummaryCell(summaryTable, "In Stock", String.valueOf(okCount), boldFont, regularFont);
        addSummaryCell(summaryTable, "Low Stock", String.valueOf(lowCount), boldFont, regularFont);
        addSummaryCell(summaryTable, "Out of Stock", String.valueOf(outCount), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Value", "₹" + formatAmount(totalValue), boldFont, regularFont);
        
        document.add(summaryTable);
    }
    
    private void addStockTable(Document document, List<DashboardDto.StockData> data, PdfFont boldFont, PdfFont regularFont) {
        // Column widths: Item Name, Category, Unit, Stock Qty, Status
        Table table = new Table(UnitValue.createPercentArray(new float[]{3f, 2f, 1f, 1.5f, 1.5f}))
                .useAllAvailableWidth();
        
        // Headers
        String[] headers = {"Item Name", "Category", "Unit", "Stock Quantity", "Status"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows
        for (DashboardDto.StockData row : data) {
            // Fetch additional info from Item if available
            String category = "";
            String unit = "";
            try {
                if (row.getItemCode() != null) {
                    Optional<Item> itemEntity = itemRepository.findByItemCode(row.getItemCode());
                    if (itemEntity.isPresent()) {
                        category = itemEntity.get().getCategory() != null ? itemEntity.get().getCategory() : "";
                        unit = itemEntity.get().getUnit() != null ? itemEntity.get().getUnit() : "";
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch item details for stock report: {}", e.getMessage());
            }
            
            String status = row.getStatus() != null ? row.getStatus() : "OK";
            if ("OUT_OF_STOCK".equals(status)) {
                status = "Out of Stock";
            } else if ("NEGATIVE".equals(status)) {
                status = "Negative";
            }
            
            table.addCell(createDataCell(row.getItemName() != null ? row.getItemName() : "N/A", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(category, regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(unit, regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(formatDecimal(row.getCurrentStock()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell(status, regularFont, TextAlignment.CENTER));
        }
        
        document.add(table);
    }
    
    // ==================== SALES REPORT ====================
    
    public byte[] generateSalesReport(List<DashboardDto.SalesData> data, LocalDate startDate, LocalDate endDate) {
        log.info("Generating Sales Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "SALES REPORT", startDate, endDate, boldFont, regularFont);
            
            // Summary
            addSalesSummary(document, data, boldFont, regularFont);
            
            // Table
            addSalesTable(document, data, boldFont, regularFont);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Sales Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addSalesSummary(Document document, List<DashboardDto.SalesData> data, PdfFont boldFont, PdfFont regularFont) {
        BigDecimal totalRevenue = data.stream()
                .map(DashboardDto.SalesData::getTotalRevenue)
                .filter(r -> r != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalQty = data.stream()
                .map(DashboardDto.SalesData::getQuantitySold)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph summaryTitle = new Paragraph("Summary")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(10)
                .setMarginBottom(8);
        document.add(summaryTitle);
        
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        addSummaryCell(summaryTable, "Total Items", String.valueOf(data.size()), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Quantity", formatDecimal(totalQty), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Revenue", "₹" + formatAmount(totalRevenue), boldFont, regularFont);
        
        document.add(summaryTable);
    }
    
    private void addSalesTable(Document document, List<DashboardDto.SalesData> data, PdfFont boldFont, PdfFont regularFont) {
        // Column widths: Code, Item Name, Quantity, Unit Price, Revenue
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.5f, 3f, 1.2f, 1.5f, 1.8f}))
                .useAllAvailableWidth();
        
        // Headers
        String[] headers = {"Item Code", "Item Name", "Qty Sold", "Unit Price", "Revenue (₹)"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows - sorted by revenue descending
        data.stream()
                .sorted((a, b) -> {
                    BigDecimal ra = a.getTotalRevenue() != null ? a.getTotalRevenue() : BigDecimal.ZERO;
                    BigDecimal rb = b.getTotalRevenue() != null ? b.getTotalRevenue() : BigDecimal.ZERO;
                    return rb.compareTo(ra);
                })
                .forEach(row -> {
                    BigDecimal qty = row.getQuantitySold() != null && row.getQuantitySold().compareTo(BigDecimal.ZERO) > 0 
                            ? row.getQuantitySold() : BigDecimal.ONE;
                    BigDecimal unitPrice = row.getTotalRevenue() != null 
                            ? row.getTotalRevenue().divide(qty, 2, RoundingMode.HALF_UP) 
                            : BigDecimal.ZERO;
                    
                    table.addCell(createDataCell(row.getItemCode() != null ? row.getItemCode() : "", regularFont, TextAlignment.LEFT));
                    table.addCell(createDataCell(row.getItemName() != null ? row.getItemName() : "N/A", regularFont, TextAlignment.LEFT));
                    table.addCell(createDataCell(formatDecimal(row.getQuantitySold()), regularFont, TextAlignment.RIGHT));
                    table.addCell(createDataCell(formatAmount(unitPrice), regularFont, TextAlignment.RIGHT));
                    table.addCell(createDataCell(formatAmount(row.getTotalRevenue()), regularFont, TextAlignment.RIGHT));
                });
        
        document.add(table);
    }
    
    // ==================== PURCHASE REPORT ====================
    
    public byte[] generatePurchaseReport(List<DashboardDto.PurchaseData> data, LocalDate startDate, LocalDate endDate) {
        log.info("Generating Purchase Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4.rotate()); // Landscape for more columns
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "PURCHASE REPORT", startDate, endDate, boldFont, regularFont);
            
            // Summary
            addPurchaseSummary(document, data, boldFont, regularFont);
            
            // Table
            addPurchaseTable(document, data, boldFont, regularFont);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Purchase Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addPurchaseSummary(Document document, List<DashboardDto.PurchaseData> data, PdfFont boldFont, PdfFont regularFont) {
        long totalPurchases = data.stream().map(d -> d.getBillNumber()).distinct().count();
        BigDecimal totalQuantity = data.stream()
                .map(DashboardDto.PurchaseData::getQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = data.stream()
                .map(DashboardDto.PurchaseData::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph summaryTitle = new Paragraph("Summary")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(10)
                .setMarginBottom(8);
        document.add(summaryTitle);
        
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        addSummaryCell(summaryTable, "Total Bills", String.valueOf(totalPurchases), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Items", String.valueOf(data.size()), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Quantity", formatDecimal(totalQuantity), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Amount", "₹" + formatAmount(totalAmount), boldFont, regularFont);
        
        document.add(summaryTable);
    }
    
    private void addPurchaseTable(Document document, List<DashboardDto.PurchaseData> data, PdfFont boldFont, PdfFont regularFont) {
        // Column widths: Bill No, Date, Party, Item, Qty, Unit Price, Total
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.2f, 1.2f, 2f, 2.5f, 0.8f, 1.2f, 1.3f}))
                .useAllAvailableWidth();
        
        // Headers
        String[] headers = {"Bill No", "Date", "Party Name", "Item Name", "Qty", "Unit Price", "Total (₹)"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows
        for (DashboardDto.PurchaseData row : data) {
            table.addCell(createDataCell(row.getBillNumber() != null ? row.getBillNumber() : "—", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(formatDate(row.getBillDate()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(row.getPartyName() != null ? row.getPartyName() : "", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(row.getItemName() != null ? row.getItemName() : "N/A", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(formatDecimal(row.getQuantity()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell(formatAmount(row.getUnitPrice()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell(formatAmount(row.getTotalAmount()), regularFont, TextAlignment.RIGHT));
        }
        
        document.add(table);
    }
    
    // ==================== PARTY REPORT ====================
    
    public byte[] generatePartyReport(List<DashboardDto.PartyData> data, LocalDate startDate, LocalDate endDate) {
        log.info("Generating Party Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "PARTY-WISE REPORT", startDate, endDate, boldFont, regularFont);
            
            // Summary
            addPartySummary(document, data, boldFont, regularFont);
            
            // Table
            addPartyTable(document, data, boldFont, regularFont);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Party Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addPartySummary(Document document, List<DashboardDto.PartyData> data, PdfFont boldFont, PdfFont regularFont) {
        long totalParties = data.size();
        long totalBills = data.stream().mapToLong(DashboardDto.PartyData::getTotalBills).sum();
        BigDecimal totalAmount = data.stream()
                .map(DashboardDto.PartyData::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph summaryTitle = new Paragraph("Summary")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(10)
                .setMarginBottom(8);
        document.add(summaryTitle);
        
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        addSummaryCell(summaryTable, "Total Parties", String.valueOf(totalParties), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Bills", String.valueOf(totalBills), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Amount", "₹" + formatAmount(totalAmount), boldFont, regularFont);
        
        document.add(summaryTable);
    }
    
    private void addPartyTable(Document document, List<DashboardDto.PartyData> data, PdfFont boldFont, PdfFont regularFont) {
        // Column widths: Party Name, Total Bills, GST Bills, Estimate Bills, Total Amount
        Table table = new Table(UnitValue.createPercentArray(new float[]{3f, 1.2f, 1.2f, 1.2f, 2f}))
                .useAllAvailableWidth();
        
        // Headers
        String[] headers = {"Party Name", "Total Bills", "GST Bills", "Estimates", "Amount (₹)"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows - sorted by amount descending
        data.stream()
                .sorted((a, b) -> {
                    BigDecimal aa = a.getTotalAmount() != null ? a.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal ab = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
                    return ab.compareTo(aa);
                })
                .forEach(row -> {
                    table.addCell(createDataCell(row.getPartyName() != null ? row.getPartyName() : "N/A", regularFont, TextAlignment.LEFT));
                    table.addCell(createDataCell(String.valueOf(row.getTotalBills()), regularFont, TextAlignment.CENTER));
                    table.addCell(createDataCell(String.valueOf(row.getGstBills()), regularFont, TextAlignment.CENTER));
                    table.addCell(createDataCell(String.valueOf(row.getEstimateBills()), regularFont, TextAlignment.CENTER));
                    table.addCell(createDataCell(formatAmount(row.getTotalAmount()), regularFont, TextAlignment.RIGHT));
                });
        
        document.add(table);
    }
    
    // ==================== EXPENSE REPORT ====================
    
    public byte[] generateExpenseReport(List<DashboardDto.ExpenseData> data, LocalDate startDate, LocalDate endDate) {
        log.info("Generating Cash Ledger Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4.rotate()); // Landscape for more columns
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "CASH LEDGER REPORT", startDate, endDate, boldFont, regularFont);
            
            // Summary
            addExpenseSummary(document, data, boldFont, regularFont);
            
            // Table
            addExpenseTable(document, data, boldFont, regularFont);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Cash Ledger Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addExpenseSummary(Document document, List<DashboardDto.ExpenseData> data, PdfFont boldFont, PdfFont regularFont) {
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        long countIn = 0;
        long countOut = 0;
        
        for (DashboardDto.ExpenseData d : data) {
            BigDecimal amt = d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO;
            if ("CASH_OUT".equalsIgnoreCase(d.getTransactionType())) {
                totalOut = totalOut.add(amt);
                countOut++;
            } else {
                totalIn = totalIn.add(amt);
                countIn++;
            }
        }
        
        Paragraph summaryTitle = new Paragraph("Summary")
                .setFont(boldFont)
                .setFontSize(12)
                .setMarginTop(10)
                .setMarginBottom(8);
        document.add(summaryTitle);
        
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        addSummaryCell(summaryTable, "Cash In Entries", String.valueOf(countIn), boldFont, regularFont);
        addSummaryCell(summaryTable, "Cash Out Entries", String.valueOf(countOut), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Cash In", "₹" + formatAmount(totalIn), boldFont, regularFont);
        addSummaryCell(summaryTable, "Total Cash Out", "₹" + formatAmount(totalOut), boldFont, regularFont);
        addSummaryCell(summaryTable, "Net Cash Flow", "₹" + formatAmount(totalIn.subtract(totalOut)), boldFont, regularFont);
        
        document.add(summaryTable);
    }
    
    private void addExpenseTable(Document document, List<DashboardDto.ExpenseData> data, PdfFont boldFont, PdfFont regularFont) {
        // Column widths: Voucher No, Date, Category, Party, Payment, Cash In, Cash Out, Description
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.2f, 1.8f, 2f, 1.2f, 1.5f, 1.5f, 2.5f}))
                .useAllAvailableWidth();
        
        // Headers
        String[] headers = {"Voucher No", "Date", "Category", "Party Name", "Payment", "Cash In (₹)", "Cash Out (₹)", "Description"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows
        for (DashboardDto.ExpenseData row : data) {
            table.addCell(createDataCell(row.getExpenseNumber() != null ? row.getExpenseNumber() : "", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(formatDate(row.getExpenseDate()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(row.getCategory() != null ? row.getCategory() : "", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(row.getPartyName() != null ? row.getPartyName() : "", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(row.getPaymentMode() != null ? row.getPaymentMode() : "", regularFont, TextAlignment.CENTER));
            
            boolean isOut = "CASH_OUT".equalsIgnoreCase(row.getTransactionType());
            BigDecimal amount = row.getAmount() != null ? row.getAmount() : BigDecimal.ZERO;
            
            if (isOut) {
                table.addCell(createDataCell("0.00", regularFont, TextAlignment.RIGHT));
                table.addCell(createDataCell(formatAmount(amount), regularFont, TextAlignment.RIGHT));
            } else {
                table.addCell(createDataCell(formatAmount(amount), regularFont, TextAlignment.RIGHT));
                table.addCell(createDataCell("0.00", regularFont, TextAlignment.RIGHT));
            }
            
            table.addCell(createDataCell(row.getDescription() != null ? row.getDescription() : "", regularFont, TextAlignment.LEFT));
        }
        
        document.add(table);
    }
    
    // ==================== CASH IN HAND REPORT ====================
    
    public byte[] generateCashInHandReport(DashboardDto.CashInHandData data, LocalDate date) {
        log.info("Generating Cash In Hand Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "CASH IN HAND REPORT", date != null ? date : LocalDate.now(), 
                    date != null ? date : LocalDate.now(), boldFont, regularFont);
            
            // Cash in hand details
            Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{2, 2}))
                    .useAllAvailableWidth()
                    .setMarginTop(20);
            
            addDetailRow(detailsTable, "Total Sales:", "₹" + formatAmount(data.getTotalSales()), boldFont, regularFont);
            addDetailRow(detailsTable, "Total Cash In:", "₹" + formatAmount(data.getTotalExpenses()), boldFont, regularFont);
            addDetailRow(detailsTable, "Liquid Cash:", "₹" + formatAmount(data.getLiquidCash() != null ? data.getLiquidCash() : BigDecimal.ZERO), boldFont, regularFont);
            addDetailRow(detailsTable, "Online Balance:", "₹" + formatAmount(data.getOnlineBalance() != null ? data.getOnlineBalance() : BigDecimal.ZERO), boldFont, regularFont);
            
            // Add separator line
            Cell separatorCell = new Cell(1, 2)
                    .setBorderTop(new SolidBorder(BORDER_COLOR, 1))
                    .setBorderBottom(Border.NO_BORDER)
                    .setBorderLeft(Border.NO_BORDER)
                    .setBorderRight(Border.NO_BORDER)
                    .setHeight(10);
            detailsTable.addCell(separatorCell);
            
            // Total Available Balance (highlighted)
            BigDecimal totalBalance = data.getTotalAvailableBalance() != null ? data.getTotalAvailableBalance() : BigDecimal.ZERO;
            Cell labelCell = new Cell().add(new Paragraph("Total Available Balance:")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setFontColor(PRIMARY_COLOR))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(CELL_PADDING);
            detailsTable.addCell(labelCell);
            
            Cell valueCell = new Cell().add(new Paragraph("₹" + formatAmount(totalBalance))
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setFontColor(totalBalance.compareTo(BigDecimal.ZERO) >= 0 ? 
                            new DeviceRgb(22, 163, 74) : new DeviceRgb(220, 38, 38))
                    .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(CELL_PADDING);
            detailsTable.addCell(valueCell);
            
            document.add(detailsTable);
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Cash In Hand Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    // ==================== COMMON HELPER METHODS ====================
    
    private void addReportHeader(Document document, String title, LocalDate startDate, LocalDate endDate, 
                                 PdfFont boldFont, PdfFont regularFont) {
        // Get company settings
        CompanySettingsDto settings = companySettingsService.getSettings();
        String companyName = settings.getCompanyName() != null && !settings.getCompanyName().isEmpty() 
                ? settings.getCompanyName() : "Billing Application";
        
        // Company name
        Paragraph company = new Paragraph(companyName)
                .setFont(boldFont)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DARK_TEXT);
        document.add(company);
        
        // Company address (if available)
        StringBuilder addressBuilder = new StringBuilder();
        if (settings.getAddress() != null && !settings.getAddress().isEmpty()) {
            addressBuilder.append(settings.getAddress());
        }
        if (settings.getCity() != null && !settings.getCity().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(settings.getCity());
        }
        if (settings.getState() != null && !settings.getState().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(settings.getState());
        }
        if (settings.getPincode() != null && !settings.getPincode().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(" - ");
            addressBuilder.append(settings.getPincode());
        }
        
        if (addressBuilder.length() > 0) {
            Paragraph address = new Paragraph(addressBuilder.toString())
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceRgb(100, 100, 100));
            document.add(address);
        }
        
        // Contact info
        StringBuilder contactBuilder = new StringBuilder();
        if (settings.getPhone() != null && !settings.getPhone().isEmpty()) {
            contactBuilder.append("Phone: ").append(settings.getPhone());
        }
        if (settings.getEmail() != null && !settings.getEmail().isEmpty()) {
            if (contactBuilder.length() > 0) contactBuilder.append(" | ");
            contactBuilder.append("Email: ").append(settings.getEmail());
        }
        if (settings.getGstin() != null && !settings.getGstin().isEmpty()) {
            if (contactBuilder.length() > 0) contactBuilder.append(" | ");
            contactBuilder.append("GSTIN: ").append(settings.getGstin());
        }
        
        if (contactBuilder.length() > 0) {
            Paragraph contact = new Paragraph(contactBuilder.toString())
                    .setFont(regularFont)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceRgb(100, 100, 100));
            document.add(contact);
        }
        
        // Horizontal line
        Table lineTable = new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth();
        Cell lineCell = new Cell()
                .setBorderTop(new SolidBorder(BORDER_COLOR, 1))
                .setBorderBottom(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setHeight(5)
                .setMarginTop(10);
        lineTable.addCell(lineCell);
        document.add(lineTable);
        
        // Report title
        Paragraph reportTitle = new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(10);
        document.add(reportTitle);
        
        // Date range
        String dateRange = formatDateLong(startDate) + " to " + formatDateLong(endDate);
        if (startDate.equals(endDate)) {
            dateRange = formatDateLong(startDate);
        }
        Paragraph dates = new Paragraph(dateRange)
                .setFont(regularFont)
                .setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(new DeviceRgb(100, 100, 100))
                .setMarginBottom(15);
        document.add(dates);
    }
    
    private void addReportFooter(Document document, PdfFont regularFont) {
        CompanySettingsDto settings = companySettingsService.getSettings();
        String companyName = settings.getCompanyName() != null && !settings.getCompanyName().isEmpty() 
                ? settings.getCompanyName() : "Billing Application";
        
        // Horizontal line
        Table lineTable = new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth();
        Cell lineCell = new Cell()
                .setBorderTop(new SolidBorder(BORDER_COLOR, 1))
                .setBorderBottom(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setHeight(5)
                .setMarginTop(20);
        lineTable.addCell(lineCell);
        document.add(lineTable);
        
        // Generated timestamp
        Paragraph footer = new Paragraph("Generated on: " + 
                LocalDate.now().format(DATE_LONG_FORMATTER) + " | Page 1")
                .setFont(regularFont)
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(new DeviceRgb(128, 128, 128))
                .setItalic()
                .setMarginTop(5);
        document.add(footer);
        
        // Slogan
        String slogan = settings.getFooterSlogan() != null && !settings.getFooterSlogan().isEmpty() 
                ? settings.getFooterSlogan() : "Thank you for your business!";
        Paragraph sloganPara = new Paragraph(slogan)
                .setFont(regularFont)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(new DeviceRgb(100, 100, 100))
                .setBold()
                .setMarginTop(5);
        document.add(sloganPara);
    }
    
    private Cell createHeaderCell(String text, PdfFont boldFont) {
        return new Cell()
                .add(new Paragraph(text).setFont(boldFont).setFontSize(10))
                .setBackgroundColor(HEADER_BG)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(CELL_PADDING)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
    }
    
    private Cell createDataCell(String text, PdfFont font, TextAlignment alignment) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(9))
                .setTextAlignment(alignment)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(CELL_PADDING)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
    }
    
    private void addSummaryCell(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        Cell cell = new Cell()
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(10)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
        
        Paragraph labelPara = new Paragraph(label)
                .setFont(regularFont)
                .setFontSize(9)
                .setFontColor(new DeviceRgb(100, 100, 100))
                .setMarginBottom(3);
        cell.add(labelPara);
        
        Paragraph valuePara = new Paragraph(value)
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(DARK_TEXT);
        cell.add(valuePara);
        
        table.addCell(cell);
    }
    
    private void addDetailRow(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        Cell labelCell = new Cell().add(new Paragraph(label).setFont(boldFont).setFontSize(11))
                .setBorder(Border.NO_BORDER)
                .setPadding(CELL_PADDING);
        table.addCell(labelCell);
        
        Cell valueCell = new Cell().add(new Paragraph(value).setFont(regularFont).setFontSize(11)
                .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setPadding(CELL_PADDING);
        table.addCell(valueCell);
    }
    
    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMATTER);
    }
    
    private String formatDateLong(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_LONG_FORMATTER);
    }
    
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    
    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }
    
    // ==================== CASH IN HAND REPORT ====================
    
    public byte[] generateCashInHandReport(CashInHandDto data) {
        log.info("Generating Cash In Hand Report PDF with iText");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Header
            addReportHeader(document, "CASH IN HAND REPORT", data.getStartDate(), data.getEndDate(), boldFont, regularFont);
            
            // Summary Cards
            addCashInHandSummary(document, data, boldFont, regularFont);
            
            // Sales Entries Table
            if (data.getSalesEntries() != null && !data.getSalesEntries().isEmpty()) {
                addCashInHandSalesTable(document, data.getSalesEntries(), boldFont, regularFont);
            }
            
            // Expense Entries Table
            if (data.getExpenseEntries() != null && !data.getExpenseEntries().isEmpty()) {
                addCashInHandExpenseTable(document, data.getExpenseEntries(), boldFont, regularFont);
            }
            
            // Footer
            addReportFooter(document, regularFont);
            
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Cash In Hand Report PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    private void addCashInHandSummary(Document document, CashInHandDto data, PdfFont boldFont, PdfFont regularFont) {
        // Row 0: Starting Balance (Yesterday's End)
        BigDecimal startLiquid = data.getStartingLiquidCash() != null ? data.getStartingLiquidCash() : BigDecimal.ZERO;
        BigDecimal startOnline = data.getStartingOnlineBalance() != null ? data.getStartingOnlineBalance() : BigDecimal.ZERO;
        BigDecimal startTotal = data.getStartingTotalBalance() != null ? data.getStartingTotalBalance() : BigDecimal.ZERO;
        Table row0 = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(10);
        row0.addCell(createSummaryCell("Starting Liquid Cash", startLiquid, new DeviceRgb(255, 251, 235), new DeviceRgb(217, 119, 6), boldFont, regularFont));
        row0.addCell(createSummaryCell("Starting Online Balance", startOnline, new DeviceRgb(239, 246, 255), new DeviceRgb(37, 99, 235), boldFont, regularFont));
        row0.addCell(createSummaryCell("Starting Total", startTotal, new DeviceRgb(243, 244, 246), new DeviceRgb(55, 65, 81), boldFont, regularFont));
        document.add(row0);
        
        // Row 1: Cash Received, Online Received, Total Received (period)
        Table row1 = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(10);
        row1.addCell(createSummaryCell("Cash Received (period)", data.getTotalCashReceived(), new DeviceRgb(232, 245, 233), new DeviceRgb(46, 125, 50), boldFont, regularFont));
        row1.addCell(createSummaryCell("Online Received (period)", data.getTotalOnlineReceived(), new DeviceRgb(227, 242, 253), new DeviceRgb(33, 150, 243), boldFont, regularFont));
        row1.addCell(createSummaryCell("Total Received (period)", data.getTotalSalesReceived(), new DeviceRgb(232, 245, 233), new DeviceRgb(46, 125, 50), boldFont, regularFont));
        document.add(row1);
        
        // Row 2: Cash Expenses, Online Expenses, Total Expenses
        Table row2 = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(10);
        row2.addCell(createSummaryCell("Cash In (Cash)", data.getTotalCashExpenses(), new DeviceRgb(255, 235, 238), new DeviceRgb(198, 40, 40), boldFont, regularFont));
        row2.addCell(createSummaryCell("Cash In (Online)", data.getTotalOnlineExpenses(), new DeviceRgb(255, 235, 238), new DeviceRgb(198, 40, 40), boldFont, regularFont));
        row2.addCell(createSummaryCell("Total Cash In", data.getTotalExpensesPaid(), new DeviceRgb(255, 235, 238), new DeviceRgb(198, 40, 40), boldFont, regularFont));
        document.add(row2);
        
        // Row 3: Liquid Cash, Online Balance, Total Available Balance
        Table row3 = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(20);
        BigDecimal liquidCash = data.getLiquidCash() != null ? data.getLiquidCash() : BigDecimal.ZERO;
        BigDecimal onlineBalance = data.getOnlineBalance() != null ? data.getOnlineBalance() : BigDecimal.ZERO;
        BigDecimal totalBalance = data.getTotalAvailableBalance() != null ? data.getTotalAvailableBalance() : BigDecimal.ZERO;
        row3.addCell(createSummaryCell("Liquid Cash (Closing)", liquidCash, new DeviceRgb(255, 243, 224), new DeviceRgb(239, 108, 0), boldFont, regularFont));
        row3.addCell(createSummaryCell("Online Balance (Closing)", onlineBalance, new DeviceRgb(227, 242, 253), new DeviceRgb(33, 150, 243), boldFont, regularFont));
        DeviceRgb totalColor = totalBalance.compareTo(BigDecimal.ZERO) >= 0 ? new DeviceRgb(21, 101, 192) : new DeviceRgb(198, 40, 40);
        Cell totalCell = new Cell().setPadding(15).setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setBackgroundColor(new DeviceRgb(227, 242, 253));
        totalCell.add(new Paragraph("Total Available Balance (Closing)").setFont(regularFont).setFontSize(10).setFontColor(new DeviceRgb(33, 150, 243)));
        totalCell.add(new Paragraph("₹ " + formatAmount(totalBalance)).setFont(boldFont).setFontSize(18).setFontColor(totalColor));
        row3.addCell(totalCell);
        document.add(row3);
    }
    
    private Cell createSummaryCell(String label, BigDecimal amount, DeviceRgb bgColor, DeviceRgb textColor, PdfFont boldFont, PdfFont regularFont) {
        Cell cell = new Cell().setPadding(15).setBorder(new SolidBorder(BORDER_COLOR, 0.5f)).setBackgroundColor(bgColor);
        cell.add(new Paragraph(label).setFont(regularFont).setFontSize(10).setFontColor(textColor));
        cell.add(new Paragraph("₹ " + formatAmount(amount != null ? amount : BigDecimal.ZERO)).setFont(boldFont).setFontSize(18).setFontColor(textColor));
        return cell;
    }
    
    private void addCashInHandSalesTable(Document document, List<CashInHandDto.SalesEntry> salesEntries, 
                                          PdfFont boldFont, PdfFont regularFont) {
        // Section Title
        Paragraph sectionTitle = new Paragraph("SALES ENTRIES (Money IN)")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(new DeviceRgb(46, 125, 50))
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);
        
        // Table with columns: Date, Type, Party, Bill No, Items Total, Received, Pending, Status
        Table table = new Table(UnitValue.createPercentArray(new float[]{12, 12, 18, 12, 12, 12, 12, 10}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        // Headers
        String[] headers = {"Date", "Type", "Party", "Bill No", "Items Total", "Received", "Pending", "Status"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows
        for (CashInHandDto.SalesEntry entry : salesEntries) {
            table.addCell(createDataCell(formatDate(entry.getDate()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(entry.getModuleType(), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(entry.getPartyName(), regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(entry.getBillNumber(), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell("₹" + formatAmount(entry.getItemsTotal()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell("₹" + formatAmount(entry.getReceivedAmount()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell("₹" + formatAmount(entry.getPendingAmount()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell(entry.getPaymentStatus(), regularFont, TextAlignment.CENTER));
        }
        
        document.add(table);
        
        // Totals
        BigDecimal totalItemsTotal = salesEntries.stream()
                .map(CashInHandDto.SalesEntry::getItemsTotal)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = salesEntries.stream()
                .map(CashInHandDto.SalesEntry::getReceivedAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPending = salesEntries.stream()
                .map(CashInHandDto.SalesEntry::getPendingAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph totals = new Paragraph("Sales Totals: Items Total = ₹" + formatAmount(totalItemsTotal) + 
                " | Received = ₹" + formatAmount(totalReceived) + " | Pending = ₹" + formatAmount(totalPending))
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(46, 125, 50))
                .setMarginBottom(20);
        document.add(totals);
    }
    
    private void addCashInHandExpenseTable(Document document, List<CashInHandDto.ExpenseEntry> expenseEntries, 
                                            PdfFont boldFont, PdfFont regularFont) {
        // Section Title
        Paragraph sectionTitle = new Paragraph("CASH IN ENTRIES (Money OUT)")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(new DeviceRgb(198, 40, 40))
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);
        
        // Table with columns: Date, Type, Party, Bill No, Total, Paid, Outstanding, Status
        Table table = new Table(UnitValue.createPercentArray(new float[]{12, 12, 18, 12, 12, 12, 12, 10}))
                .useAllAvailableWidth()
                .setMarginBottom(15);
        
        // Headers
        String[] headers = {"Date", "Type", "Party", "Bill No", "Total", "Paid", "Outstanding", "Status"};
        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, boldFont));
        }
        
        // Data rows
        for (CashInHandDto.ExpenseEntry entry : expenseEntries) {
            table.addCell(createDataCell(formatDate(entry.getDate()), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(entry.getModuleType(), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell(entry.getPartyName() != null ? entry.getPartyName() : "-", regularFont, TextAlignment.LEFT));
            table.addCell(createDataCell(entry.getBillNumber(), regularFont, TextAlignment.CENTER));
            table.addCell(createDataCell("₹" + formatAmount(entry.getItemsTotal()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell("₹" + formatAmount(entry.getPaidAmount()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell("₹" + formatAmount(entry.getPendingAmount()), regularFont, TextAlignment.RIGHT));
            table.addCell(createDataCell(entry.getPaymentStatus(), regularFont, TextAlignment.CENTER));
        }
        
        document.add(table);
        
        // Totals
        BigDecimal totalItemsTotal = expenseEntries.stream()
                .map(CashInHandDto.ExpenseEntry::getItemsTotal)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = expenseEntries.stream()
                .map(CashInHandDto.ExpenseEntry::getPaidAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPending = expenseEntries.stream()
                .map(CashInHandDto.ExpenseEntry::getPendingAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Paragraph totals = new Paragraph("Cash In Totals: Total = ₹" + formatAmount(totalItemsTotal) + 
                " | Paid = ₹" + formatAmount(totalPaid) + " | Outstanding = ₹" + formatAmount(totalPending))
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(198, 40, 40))
                .setMarginBottom(20);
        document.add(totals);
    }
}

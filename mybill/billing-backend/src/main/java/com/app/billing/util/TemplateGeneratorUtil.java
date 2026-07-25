package com.app.billing.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone utility to generate all missing Word templates.
 * Each generated file is wired in {@link com.app.billing.service.TemplateResolver} and used for PDF output
 * (e.g. {@link com.app.billing.service.ReportWordTemplateService}, {@link com.app.billing.service.QuotationWordTemplateService}).
 * Do not delete template generation without updating those services.
 *
 * <p>Run this class's main method to generate all templates in the correct locations.</p>
 */
public class TemplateGeneratorUtil {

    private static final String BASE_DIR;
    
    static {
        // Determine base directory - look for billing-backend directory
        String userDir = System.getProperty("user.dir");
        if (userDir.contains("billing-backend")) {
            BASE_DIR = userDir;
        } else {
            // Assume we're in project root, go to billing-backend
            BASE_DIR = Paths.get(userDir, "billing-backend").toString();
        }
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("Generating missing Word templates...");
            System.out.println("Base directory: " + BASE_DIR);
            
            generateQuotationTemplate();
            generateSalesReportTemplate();
            generatePurchaseReportTemplate();
            generatePartyReportTemplate();
            generateBillingReportTemplate();
            generateStockReportTemplate();
            generateExpenseReportTemplate();
            
            System.out.println("\n✅ All templates generated successfully!");
            System.out.println("Templates are located in: " + BASE_DIR + "/src/main/resources/templates/");
        } catch (Exception e) {
            System.err.println("❌ Error generating templates: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String getTemplatePath(String relativePath) {
        return Paths.get(BASE_DIR, relativePath).toString();
    }
    
    private static void generateQuotationTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/quotation/QuotationTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ QuotationTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating QuotationTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/quotation"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "QUOTATION");
            
            // Quotation Information
            addInfoSection(document, 
                "Quotation No: ${quotation_number}",
                "Quotation Date: ${quotation_date}",
                "Valid Till: ${valid_till}",
                "Delivery Date: ${delivery_date}"
            );
            
            addHorizontalLine(document);
            
            // Party Information
            addSectionHeader(document, "Party Details");
            addInfoSection(document,
                "Party Name: ${party_name}",
                "Address: ${party_address}",
                "Shipping Address: ${ship_to_address}"
            );
            
            addHorizontalLine(document);
            
            // Items Table
            addSectionHeader(document, "Items");
            XWPFTable table = createItemsTable(document, new String[]{
                "Item Name", "Category", "EN Code", "Qty", "Unit", "Rate", "Tax", "Amount"
            });
            
            // Data row template - CRITICAL: #foreach in first cell, #end in last cell
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF"); // Invisible
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.item_name");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.category");
            setTableCell(dataRow.getCell(2), "$item.en_code");
            setTableCell(dataRow.getCell(3), "$item.quantity");
            setTableCell(dataRow.getCell(4), "$item.unit");
            setTableCell(dataRow.getCell(5), "$item.rate");
            setTableCell(dataRow.getCell(6), "$item.tax");
            
            XWPFParagraph dataPara8 = dataRow.getCell(7).getParagraphs().get(0);
            XWPFRun dataRun8 = dataPara8.createRun();
            dataRun8.setText("$item.amount");
            dataRun8.setFontSize(10);
            XWPFRun endRun = dataPara8.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF"); // Invisible
            
            addHorizontalLine(document);
            addTotalsSection(document);
            addCommonFooter(document);
            
            saveDocument(document, templatePath);
            System.out.println("✓ QuotationTemplate.docx created");
        }
    }
    
    private static void generateSalesReportTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/reports/SalesReportTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ SalesReportTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating SalesReportTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/reports"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "SALES REPORT");
            
            XWPFParagraph infoPara = document.createParagraph();
            infoPara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun infoRun = infoPara.createRun();
            infoRun.setText("Date Range: ${start_date} to ${end_date}");
            infoRun.setBold(true);
            
            addHorizontalLine(document);
            
            XWPFTable table = createItemsTable(document, new String[]{
                "Invoice No", "Date", "Party Name", "Item Name", "Category", "Qty", "Amount"
            });
            
            // Data row with Velocity loop
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF");
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.invoice_no");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.date");
            setTableCell(dataRow.getCell(2), "$item.party_name");
            setTableCell(dataRow.getCell(3), "$item.item_name");
            setTableCell(dataRow.getCell(4), "$item.category");
            setTableCell(dataRow.getCell(5), "$item.quantity");
            
            XWPFParagraph dataPara7 = dataRow.getCell(6).getParagraphs().get(0);
            XWPFRun dataRun7 = dataPara7.createRun();
            dataRun7.setText("$item.amount");
            dataRun7.setFontSize(10);
            XWPFRun endRun = dataPara7.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF");
            
            addHorizontalLine(document);
            
            XWPFParagraph summaryPara = document.createParagraph();
            summaryPara.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun summaryRun = summaryPara.createRun();
            summaryRun.setText("Total Sales Amount: ${total_amount}");
            summaryRun.setBold(true);
            summaryRun.setFontSize(14);
            
            addCommonFooter(document);
            saveDocument(document, templatePath);
            System.out.println("✓ SalesReportTemplate.docx created");
        }
    }
    
    private static void generatePurchaseReportTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/reports/PurchaseReportTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ PurchaseReportTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating PurchaseReportTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/reports"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "PURCHASE REPORT");
            
            addInfoSection(document,
                "Supplier Name: ${supplier_name}",
                "Purchase Entry No: ${entry_no}",
                "Purchase Invoice No: ${invoice_no}",
                "Date: ${date}",
                "Payment Status: ${payment_status}"
            );
            
            addHorizontalLine(document);
            
            XWPFTable table = createItemsTable(document, new String[]{
                "Item", "Category", "Qty", "Unit", "Amount"
            });
            
            // Data row with Velocity loop
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF");
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.item_name");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.category");
            setTableCell(dataRow.getCell(2), "$item.quantity");
            setTableCell(dataRow.getCell(3), "$item.unit");
            
            XWPFParagraph dataPara5 = dataRow.getCell(4).getParagraphs().get(0);
            XWPFRun dataRun5 = dataPara5.createRun();
            dataRun5.setText("$item.amount");
            dataRun5.setFontSize(10);
            XWPFRun endRun = dataPara5.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF");
            
            addHorizontalLine(document);
            
            XWPFParagraph totalPara = document.createParagraph();
            totalPara.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun totalRun = totalPara.createRun();
            totalRun.setText("Total Purchase Amount: ${total_amount}");
            totalRun.setBold(true);
            totalRun.setFontSize(14);
            
            addCommonFooter(document);
            saveDocument(document, templatePath);
            System.out.println("✓ PurchaseReportTemplate.docx created");
        }
    }
    
    private static void generatePartyReportTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/reports/PartyReportTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ PartyReportTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating PartyReportTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/reports"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "PARTY REPORT");
            
            addInfoSection(document,
                "Party Name: ${party_name}",
                "Party Type: ${party_type}",
                "Phone Number: ${phone_number}"
            );
            
            addHorizontalLine(document);
            
            XWPFTable table = createItemsTable(document, new String[]{
                "Invoice No", "Date", "Bill Amount", "Payment Status"
            });
            
            // Data row with Velocity loop
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF");
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.invoice_no");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.date");
            setTableCell(dataRow.getCell(2), "$item.bill_amount");
            
            XWPFParagraph dataPara4 = dataRow.getCell(3).getParagraphs().get(0);
            XWPFRun dataRun4 = dataPara4.createRun();
            dataRun4.setText("$item.payment_status");
            dataRun4.setFontSize(10);
            XWPFRun endRun = dataPara4.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF");
            
            addHorizontalLine(document);
            
            XWPFParagraph totalPara = document.createParagraph();
            totalPara.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun totalRun = totalPara.createRun();
            totalRun.setText("Party-wise Total Amount: ${total_amount}");
            totalRun.setBold(true);
            totalRun.setFontSize(14);
            
            addCommonFooter(document);
            saveDocument(document, templatePath);
            System.out.println("✓ PartyReportTemplate.docx created");
        }
    }
    
    private static void generateBillingReportTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/reports/BillingReportTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ BillingReportTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating BillingReportTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/reports"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "BILLING REPORT");
            
            addInfoSection(document,
                "Report Period: ${start_date} to ${end_date}",
                "Generated Date: ${generated_date}"
            );
            
            addHorizontalLine(document);
            
            // Billing Report shows period summaries, not invoice items
            XWPFTable table = createItemsTable(document, new String[]{
                "Date", "Invoice Count", "Total Amount"
            });
            
            // Data row with Velocity loop
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF");
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.item_name");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.category");
            
            XWPFParagraph dataPara3 = dataRow.getCell(2).getParagraphs().get(0);
            XWPFRun dataRun3 = dataPara3.createRun();
            dataRun3.setText("$item.total");
            dataRun3.setFontSize(10);
            XWPFRun endRun = dataPara3.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF");
            
            addHorizontalLine(document);
            addTotalsSection(document);
            
            XWPFParagraph createdPara = document.createParagraph();
            XWPFRun createdRun = createdPara.createRun();
            createdRun.setText("Created By: ${created_by}");
            createdRun.setItalic(true);
            
            addCommonFooter(document);
            saveDocument(document, templatePath);
            System.out.println("✓ BillingReportTemplate.docx created");
        }
    }
    
    private static void generateStockReportTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/reports/StockReportTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ StockReportTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating StockReportTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/reports"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "STOCK REPORT");
            
            XWPFTable table = createItemsTable(document, new String[]{
                "Item Name", "Category", "Unit", "Stock Quantity", "Status"
            });
            
            // Data row with Velocity loop
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF");
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.item_name");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.category");
            setTableCell(dataRow.getCell(2), "$item.unit");
            setTableCell(dataRow.getCell(3), "$item.stock_quantity");
            
            XWPFParagraph dataPara5 = dataRow.getCell(4).getParagraphs().get(0);
            XWPFRun dataRun5 = dataPara5.createRun();
            dataRun5.setText("$item.status");
            dataRun5.setFontSize(10);
            XWPFRun endRun = dataPara5.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF");
            
            addHorizontalLine(document);
            addCommonFooter(document);
            saveDocument(document, templatePath);
            System.out.println("✓ StockReportTemplate.docx created");
        }
    }
    
    private static void generateExpenseReportTemplate() throws IOException {
        String templatePath = getTemplatePath("src/main/resources/templates/reports/ExpenseReportTemplate.docx");
        if (Files.exists(Paths.get(templatePath))) {
            System.out.println("✓ ExpenseReportTemplate.docx already exists");
            return;
        }
        
        System.out.println("Generating ExpenseReportTemplate.docx...");
        createDirectoryIfNotExists(getTemplatePath("src/main/resources/templates/reports"));
        
        try (XWPFDocument document = new XWPFDocument()) {
            setupDocumentProperties(document);
            addCommonHeader(document, "EXPENSE REPORT");
            
            XWPFTable table = createItemsTable(document, new String[]{
                "Expense No", "Date", "Category", "Party Name", "Payment Mode", "Amount", "Description"
            });
            
            // Data row with Velocity loop
            XWPFTableRow dataRow = table.createRow();
            XWPFParagraph dataPara1 = dataRow.getCell(0).getParagraphs().get(0);
            XWPFRun foreachRun = dataPara1.createRun();
            foreachRun.setText("#foreach($item in $items)");
            foreachRun.setFontSize(1);
            foreachRun.setColor("FFFFFF");
            XWPFRun dataRun1 = dataPara1.createRun();
            dataRun1.setText("$item.expense_number");
            dataRun1.setFontSize(10);
            
            setTableCell(dataRow.getCell(1), "$item.date");
            setTableCell(dataRow.getCell(2), "$item.category");
            setTableCell(dataRow.getCell(3), "$item.party_name");
            setTableCell(dataRow.getCell(4), "$item.payment_mode");
            setTableCell(dataRow.getCell(5), "$item.amount");
            
            XWPFParagraph dataPara7 = dataRow.getCell(6).getParagraphs().get(0);
            XWPFRun dataRun7 = dataPara7.createRun();
            dataRun7.setText("$item.description");
            dataRun7.setFontSize(10);
            XWPFRun endRun = dataPara7.createRun();
            endRun.setText("#end");
            endRun.setFontSize(1);
            endRun.setColor("FFFFFF");
            
            ensureTableGrid(table, new long[]{1440, 1440, 2160, 2160, 1440, 1440, 2880});
            
            addHorizontalLine(document);
            addCommonFooter(document);
            saveDocument(document, templatePath);
            System.out.println("✓ ExpenseReportTemplate.docx created");
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Ensure table has proper grid columns for PDF conversion
     * This is CRITICAL - without this, PDF conversion will fail with NullPointerException
     */
    private static void ensureTableGrid(XWPFTable table, long[] columnWidths) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl ctTbl = table.getCTTbl();
            if (ctTbl != null) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid grid = ctTbl.getTblGrid();
                if (grid == null) {
                    grid = ctTbl.addNewTblGrid();
                }
                // Clear existing columns and add new ones
                if (grid.getGridColList() != null) {
                    grid.getGridColList().clear();
                }
                // Add grid columns with specified widths
                for (long width : columnWidths) {
                    grid.addNewGridCol().setW(java.math.BigInteger.valueOf(width));
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not create table grid: " + e.getMessage());
        }
    }
    
    private static void setupDocumentProperties(XWPFDocument document) {
        try {
            if (document.getStyles() == null) {
                document.createStyles();
            }
            
            if (document.getDocument() != null && document.getDocument().getBody() != null) {
                var sectPr = document.getDocument().getBody().getSectPr();
                if (sectPr == null) {
                    sectPr = document.getDocument().getBody().addNewSectPr();
                }
                
                if (sectPr.getPgSz() == null) {
                    var pageSz = sectPr.addNewPgSz();
                    pageSz.setW(java.math.BigInteger.valueOf(11906)); // A4 width
                    pageSz.setH(java.math.BigInteger.valueOf(16838)); // A4 height
                }
                
                if (sectPr.getPgMar() == null) {
                    var pgMar = sectPr.addNewPgMar();
                    pgMar.setLeft(java.math.BigInteger.valueOf(720));
                    pgMar.setRight(java.math.BigInteger.valueOf(720));
                    pgMar.setTop(java.math.BigInteger.valueOf(720));
                    pgMar.setBottom(java.math.BigInteger.valueOf(720));
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not setup document properties: " + e.getMessage());
        }
    }
    
    private static void addCommonHeader(XWPFDocument document, String documentTitle) {
        // Company Logo placeholder
        XWPFParagraph logoPara = document.createParagraph();
        logoPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun logoRun = logoPara.createRun();
        logoRun.setText("#if($company_logo)$company_logo#end");
        logoRun.setFontSize(10);
        logoRun.setColor("808080");
        
        // Company Name
        XWPFParagraph companyPara = document.createParagraph();
        companyPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun companyRun = companyPara.createRun();
        companyRun.setText("${company_name}");
        companyRun.setBold(true);
        companyRun.setFontSize(18);
        
        // Company Address
        XWPFParagraph addressPara = document.createParagraph();
        addressPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun addressRun = addressPara.createRun();
        addressRun.setText("${company_address}");
        addressRun.setFontSize(10);
        
        // Contact Details
        XWPFParagraph contactPara = document.createParagraph();
        contactPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun contactRun = contactPara.createRun();
        contactRun.setText("Phone: ${company_phone} | Email: ${company_email} | GSTIN: ${company_gstin}");
        contactRun.setFontSize(9);
        
        addHorizontalLine(document);
        
        // Document Title
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingAfter(200);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(documentTitle);
        titleRun.setBold(true);
        titleRun.setFontSize(16);
    }
    
    private static void addHorizontalLine(XWPFDocument document) {
        XWPFParagraph linePara = document.createParagraph();
        XWPFRun lineRun = linePara.createRun();
        lineRun.setText("________________________________________________________________________");
        lineRun.setColor("000000");
        linePara.setSpacingAfter(100);
        linePara.setSpacingBefore(100);
    }
    
    private static void addSectionHeader(XWPFDocument document, String headerText) {
        XWPFParagraph headerPara = document.createParagraph();
        XWPFRun headerRun = headerPara.createRun();
        headerRun.setText(headerText);
        headerRun.setBold(true);
        headerRun.setFontSize(12);
        headerPara.setSpacingAfter(100);
    }
    
    private static void addInfoSection(XWPFDocument document, String... lines) {
        XWPFParagraph para = document.createParagraph();
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = para.createRun();
            run.setText(lines[i]);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }
    
    private static XWPFTable createItemsTable(XWPFDocument document, String[] headers) {
        XWPFTable table = document.createTable(1, headers.length);
        table.setWidth("100%");
        
        // CRITICAL: Set up table grid for PDF conversion
        // Calculate equal column widths (10000 twips total, divided by number of columns)
        long[] columnWidths = new long[headers.length];
        long widthPerColumn = 10000L / headers.length;
        for (int i = 0; i < headers.length; i++) {
            columnWidths[i] = widthPerColumn;
        }
        ensureTableGrid(table, columnWidths);
        
        // Header row
        for (int i = 0; i < headers.length; i++) {
            setTableHeaderCell(table.getRow(0).getCell(i), headers[i]);
        }
        
        return table;
    }
    
    private static void addTotalsSection(XWPFDocument document) {
        XWPFTable totalsTable = document.createTable(4, 2);
        totalsTable.setWidth("50%");
        
        // CRITICAL: Set up table grid for PDF conversion
        ensureTableGrid(totalsTable, new long[]{7000, 3000}); // 70%, 30%
        
        XWPFParagraph para1 = totalsTable.getRow(0).getCell(0).getParagraphs().get(0);
        para1.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run1 = para1.createRun();
        run1.setText("Subtotal:");
        setTableCell(totalsTable.getRow(0).getCell(1), "${subtotal}");
        
        XWPFParagraph para2 = totalsTable.getRow(1).getCell(0).getParagraphs().get(0);
        para2.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run2 = para2.createRun();
        run2.setText("Tax Amount:");
        setTableCell(totalsTable.getRow(1).getCell(1), "${tax_amount}");
        
        XWPFParagraph para3 = totalsTable.getRow(2).getCell(0).getParagraphs().get(0);
        para3.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run3 = para3.createRun();
        run3.setText("Discount:");
        setTableCell(totalsTable.getRow(2).getCell(1), "${discount_amount}");
        
        XWPFParagraph totalPara = totalsTable.getRow(3).getCell(0).getParagraphs().get(0);
        totalPara.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun totalRun = totalPara.createRun();
        totalRun.setText("Grand Total:");
        totalRun.setBold(true);
        
        XWPFParagraph totalValuePara = totalsTable.getRow(3).getCell(1).getParagraphs().get(0);
        totalValuePara.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun totalValueRun = totalValuePara.createRun();
        totalValueRun.setText("${total_amount}");
        totalValueRun.setBold(true);
    }
    
    private static void addCommonFooter(XWPFDocument document) {
        addHorizontalLine(document);
        
        XWPFParagraph footerPara = document.createParagraph();
        footerPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun footerRun = footerPara.createRun();
        footerRun.setText("Generated on: ${generated_date} | Page ${page_number}");
        footerRun.setFontSize(8);
        footerRun.setColor("808080");
        footerRun.setItalic(true);
        
        XWPFParagraph sloganPara = document.createParagraph();
        sloganPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun sloganRun = sloganPara.createRun();
        sloganRun.setText("${footer_slogan}");
        sloganRun.setFontSize(9);
        sloganRun.setItalic(true);
    }
    
    private static void setTableHeaderCell(XWPFTableCell cell, String text) {
        cell.setColor("E0E0E0");
        XWPFParagraph para = cell.getParagraphs().get(0);
        para.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(10);
    }
    
    private static void setTableCell(XWPFTableCell cell, String text) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        para.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(10);
    }
    
    private static void createDirectoryIfNotExists(String dirPath) throws IOException {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
    
    private static void saveDocument(XWPFDocument document, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        
        try (FileOutputStream out = new FileOutputStream(filePath)) {
            document.write(out);
        }
    }
}


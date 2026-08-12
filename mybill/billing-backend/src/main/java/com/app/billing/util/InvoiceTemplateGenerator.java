package com.app.billing.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class to generate the InvoiceTemplate.docx file programmatically.
 * 
 * This can be run as a standalone utility to create the initial template.
 * After generation, you can open it in Word and fine-tune the formatting.
 * 
 * Usage:
 *   Run this class's main method, then open the generated file in Word
 *   to adjust formatting, colors, and styling to match your sample bill.
 */
public class InvoiceTemplateGenerator {

    public static void main(String[] args) {
        try {
            String templatePath = "src/main/resources/templates/invoice/InvoiceTemplate.docx";
            populateTemplate(templatePath);
            System.out.println("Template populated successfully at: " + templatePath);
            System.out.println("All placeholders and structure have been added. Open in Word to adjust formatting.");
        } catch (Exception e) {
            System.err.println("Error populating template: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Populates the existing InvoiceTemplate.docx file with all required content and placeholders
     */
    public static void populateTemplate(String filePath) throws IOException {
        generateTemplate(filePath);
    }

    /**
     * Helper method to set cell padding (in twips)
     */
    private static void setCellPadding(XWPFTableCell cell, int paddingTwips) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar cellMar = tcPr.getTcMar();
            if (cellMar == null) {
                cellMar = tcPr.addNewTcMar();
            }
            // Set all margins (top, bottom, left, right)
            cellMar.addNewTop().setW(java.math.BigInteger.valueOf(paddingTwips));
            cellMar.addNewBottom().setW(java.math.BigInteger.valueOf(paddingTwips));
            cellMar.addNewLeft().setW(java.math.BigInteger.valueOf(paddingTwips));
            cellMar.addNewRight().setW(java.math.BigInteger.valueOf(paddingTwips));
        } catch (Exception e) {
            System.err.println("Warning: Could not set cell padding: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to set vertical alignment of cell content
     * @param cell The table cell
     * @param alignment "top", "center", or "bottom"
     */
    private static void setVerticalAlignment(XWPFTableCell cell, String alignment) {
        try {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVerticalJc vAlign = tcPr.getVAlign();
            if (vAlign == null) {
                vAlign = tcPr.addNewVAlign();
            }
            if ("center".equalsIgnoreCase(alignment)) {
                vAlign.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc.CENTER);
            } else if ("bottom".equalsIgnoreCase(alignment)) {
                vAlign.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc.BOTTOM);
            } else {
                vAlign.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc.TOP);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not set vertical alignment: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to ensure table has proper grid columns for PDF conversion
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
    
    public static void generateTemplate(String outputPath) throws IOException {
        XWPFDocument document = new XWPFDocument();
        
        // Ensure document has styles part - required for PDF conversion
        try {
            if (document.getStyles() == null) {
                document.createStyles();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not create styles: " + e.getMessage());
        }
        
        // Ensure document body has section properties with page size - required for PDF conversion
        try {
            if (document.getDocument() != null && document.getDocument().getBody() != null) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sectPr = 
                    document.getDocument().getBody().getSectPr();
                
                if (sectPr == null) {
                    sectPr = document.getDocument().getBody().addNewSectPr();
                }
                
                // Set page size (A4: 11906 x 16838 twips = 8.27" x 11.69")
                if (sectPr.getPgSz() == null) {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz pageSz = sectPr.addNewPgSz();
                    pageSz.setW(java.math.BigInteger.valueOf(11906)); // A4 width in twips
                    pageSz.setH(java.math.BigInteger.valueOf(16838)); // A4 height in twips
                }
                
                // Set page margins - reduced for single page layout
                if (sectPr.getPgMar() == null) {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar pgMar = sectPr.addNewPgMar();
                    pgMar.setLeft(java.math.BigInteger.valueOf(720));   // 0.5 inch = 720 twips
                    pgMar.setRight(java.math.BigInteger.valueOf(720));
                    pgMar.setTop(java.math.BigInteger.valueOf(720));
                    pgMar.setBottom(java.math.BigInteger.valueOf(720));
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not create section properties: " + e.getMessage());
        }

        // ========== HEADER SECTION ==========
        // Create a 2-column table for header
        XWPFTable headerTable = document.createTable(1, 2);
        headerTable.setWidth(10000); // 100% in twips (10,000 twips = 100%)
        ensureTableGrid(headerTable, new long[]{2500, 7500}); // 25%, 75%
        
        // Left column: Title and Logo placeholder
        XWPFTableCell leftCell = headerTable.getRow(0).getCell(0);
        leftCell.setWidth("2500"); // 25% in twips
        setCellPadding(leftCell, 50); // Minimal padding for compact layout
        setVerticalAlignment(leftCell, "top");
        
        XWPFParagraph leftPara1 = leftCell.addParagraph();
        leftPara1.setSpacingAfter(50); // Reduced spacing
        XWPFRun leftRun1 = leftPara1.createRun();
        leftRun1.setText("BILL OF SUPPLY");
        leftRun1.setBold(true);
        leftRun1.setFontSize(11);
        
        XWPFParagraph leftPara2 = leftCell.addParagraph();
        leftPara2.setSpacingAfter(100); // Reduced spacing
        XWPFRun leftRun2 = leftPara2.createRun();
        leftRun2.setText("ORIGINAL FOR RECIPIENT");
        leftRun2.setBold(true);
        leftRun2.setFontSize(10);
        
        // Logo placeholder - only show if logo exists
        XWPFParagraph leftPara3 = leftCell.addParagraph();
        XWPFRun leftRun3 = leftPara3.createRun();
        leftRun3.setText("#if($company_logo)$company_logo#end");
        leftRun3.setItalic(true);
        leftRun3.setColor("808080");
        
        // Right column: Company info
        XWPFTableCell rightCell = headerTable.getRow(0).getCell(1);
        rightCell.setWidth("7500"); // 75% in twips
        setCellPadding(rightCell, 50);
        setVerticalAlignment(rightCell, "top");
        
        XWPFParagraph rightPara1 = rightCell.addParagraph();
        rightPara1.setSpacingAfter(50); // Reduced spacing
        XWPFRun rightRun1 = rightPara1.createRun();
        rightRun1.setText("$company_name");
        rightRun1.setBold(true);
        rightRun1.setFontSize(14); // Reduced from 18
        rightRun1.setColor("0066CC");
        
        XWPFParagraph rightPara2 = rightCell.addParagraph();
        rightPara2.setSpacingAfter(30); // Reduced spacing
        XWPFRun rightRun2 = rightPara2.createRun();
        rightRun2.setText("$company_address");
        rightRun2.setFontSize(9); // Reduced from 10
        
        XWPFParagraph rightPara3 = rightCell.addParagraph();
        rightPara3.setSpacingAfter(30); // Reduced spacing
        XWPFRun rightRun3 = rightPara3.createRun();
        rightRun3.setText("#if($company_gstin)GSTIN: $company_gstin#end");
        rightRun3.setFontSize(9);
        
        XWPFParagraph rightPara4 = rightCell.addParagraph();
        rightPara4.setSpacingAfter(30); // Reduced spacing
        XWPFRun rightRun4 = rightPara4.createRun();
        rightRun4.setText("#if($company_email)Email: $company_email#end");
        rightRun4.setFontSize(9);
        
        XWPFParagraph rightPara5 = rightCell.addParagraph();
        XWPFRun rightRun5 = rightPara5.createRun();
        rightRun5.setText("#if($company_phone)Phone: $company_phone#end");
        rightRun5.setFontSize(9);
        
        // Minimal spacing
        XWPFParagraph spacer1 = document.createParagraph();
        spacer1.setSpacingAfter(50); // Minimal spacing
        
        // ========== INVOICE INFO BAR ==========
        XWPFTable infoTable = document.createTable(1, 3);
        infoTable.setWidth(10000); // 100% in twips
        ensureTableGrid(infoTable, new long[]{3333, 3334, 3333}); // 33.33%, 33.34%, 33.33%
        
        XWPFTableCell infoCell1 = infoTable.getRow(0).getCell(0);
        infoCell1.setWidth("3333"); // 33.33% in twips
        infoCell1.setColor("1E3A8A"); // Dark blue like sample
        setCellPadding(infoCell1, 50);
        setVerticalAlignment(infoCell1, "center");
        XWPFParagraph infoPara1 = infoCell1.addParagraph();
        infoPara1.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun infoRun1 = infoPara1.createRun();
        infoRun1.setText("Invoice No.: $invoice_no");
        infoRun1.setBold(true);
        infoRun1.setFontSize(10);
        infoRun1.setColor("FFFFFF"); // White text
        
        XWPFTableCell infoCell2 = infoTable.getRow(0).getCell(1);
        infoCell2.setWidth("3334"); // 33.34% in twips
        infoCell2.setColor("1E3A8A"); // Dark blue
        setCellPadding(infoCell2, 200);
        setVerticalAlignment(infoCell2, "center");
        XWPFParagraph infoPara2 = infoCell2.addParagraph();
        infoPara2.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun infoRun2 = infoPara2.createRun();
        infoRun2.setText("Invoice Date: $invoice_date");
        infoRun2.setBold(true);
        infoRun2.setFontSize(10);
        infoRun2.setColor("FFFFFF"); // White text
        
        XWPFTableCell infoCell3 = infoTable.getRow(0).getCell(2);
        infoCell3.setWidth("3333"); // 33.33% in twips
        infoCell3.setColor("1E3A8A"); // Dark blue
        setCellPadding(infoCell3, 200);
        setVerticalAlignment(infoCell3, "center");
        XWPFParagraph infoPara3 = infoCell3.addParagraph();
        infoPara3.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun infoRun3 = infoPara3.createRun();
        infoRun3.setText("Due Date: $due_date");
        infoRun3.setBold(true);
        infoRun3.setFontSize(10);
        infoRun3.setColor("FFFFFF"); // White text
        
        // Minimal spacing
        XWPFParagraph spacer2 = document.createParagraph();
        spacer2.setSpacingAfter(50);
        
        // ========== CUSTOMER DETAILS ==========
        XWPFTable customerTable = document.createTable(1, 2);
        customerTable.setWidth(10000); // 100% in twips
        ensureTableGrid(customerTable, new long[]{5000, 5000}); // 50%, 50%
        
        // Bill To
        XWPFTableCell billToCell = customerTable.getRow(0).getCell(0);
        billToCell.setWidth("5000"); // 50% in twips
        setCellPadding(billToCell, 50);
        setVerticalAlignment(billToCell, "top");
        
        XWPFParagraph billToPara1 = billToCell.addParagraph();
        billToPara1.setSpacingAfter(100);
        XWPFRun billToRun1 = billToPara1.createRun();
        billToRun1.setText("BILL TO:");
        billToRun1.setBold(true);
        billToRun1.setFontSize(10);
        
        XWPFParagraph billToPara2 = billToCell.addParagraph();
        billToPara2.setSpacingAfter(50);
        XWPFRun billToRun2 = billToPara2.createRun();
        billToRun2.setText("$customer_name");
        billToRun2.setFontSize(10);
        
        XWPFParagraph billToPara3 = billToCell.addParagraph();
        billToPara3.setSpacingAfter(50);
        XWPFRun billToRun3 = billToPara3.createRun();
        billToRun3.setText("#if($customer_phone)Mobile: $customer_phone#end");
        billToRun3.setFontSize(10);
        
        XWPFParagraph billToPara4 = billToCell.addParagraph();
        billToPara4.setSpacingAfter(50);
        XWPFRun billToRun4 = billToPara4.createRun();
        billToRun4.setText("#if($place_of_supply)Place of Supply: $place_of_supply#end");
        billToRun4.setFontSize(10);
        
        XWPFParagraph billToPara5 = billToCell.addParagraph();
        XWPFRun billToRun5 = billToPara5.createRun();
        billToRun5.setText("#if($customer_gstin)GSTIN: $customer_gstin#end");
        billToRun5.setFontSize(10);
        
        // Ship To
        XWPFTableCell shipToCell = customerTable.getRow(0).getCell(1);
        shipToCell.setWidth("5000"); // 50% in twips
        setCellPadding(shipToCell, 50);
        setVerticalAlignment(shipToCell, "top");
        
        XWPFParagraph shipToPara1 = shipToCell.addParagraph();
        shipToPara1.setSpacingAfter(100);
        XWPFRun shipToRun1 = shipToPara1.createRun();
        shipToRun1.setText("SHIP TO:");
        shipToRun1.setBold(true);
        shipToRun1.setFontSize(10);
        
        XWPFParagraph shipToPara2 = shipToCell.addParagraph();
        shipToPara2.setSpacingAfter(50);
        XWPFRun shipToRun2 = shipToPara2.createRun();
        shipToRun2.setText("$ship_to_name");
        shipToRun2.setFontSize(10);
        
        XWPFParagraph shipToPara3 = shipToCell.addParagraph();
        XWPFRun shipToRun3 = shipToPara3.createRun();
        shipToRun3.setText("$ship_to_address");
        shipToRun3.setFontSize(10);
        
        // Minimal spacing
        XWPFParagraph spacer3 = document.createParagraph();
        spacer3.setSpacingAfter(50); // Reduced spacing
        
        // ========== ITEMS TABLE ==========
        // Create ONE table with header row and ONE data row template
        // XDocReport will repeat the data row for each item within the same table
        XWPFTable itemsTable = document.createTable(2, 5); // Header row + 1 data row template
        itemsTable.setWidth(10000); // 100% in twips
        
        // Ensure table has proper grid for PDF conversion
        ensureTableGrid(itemsTable, new long[]{4000, 1500, 1500, 1500, 1500}); // 40%, 15%, 15%, 15%, 15%
        
        // Note: Table header row repetition on page breaks is handled automatically by Word/PDF converter
        // when the table structure is correct (single table with header + data rows)
        
        // Header row (row 0) - appears only once
        XWPFTableRow headerRow = itemsTable.getRow(0);
        headerRow.getCell(0).setWidth("4000"); // 40% in twips
        headerRow.getCell(1).setWidth("1500"); // 15% in twips
        headerRow.getCell(2).setWidth("1500"); // 15% in twips
        headerRow.getCell(3).setWidth("1500"); // 15% in twips
        headerRow.getCell(4).setWidth("1500"); // 15% in twips
        
        // Set header background color and MINIMAL padding for compact look
        for (int i = 0; i < 5; i++) {
            headerRow.getCell(i).setColor("E5E7EB");
            setCellPadding(headerRow.getCell(i), 50); // Minimal padding (50 twips = ~0.035 inches)
            setVerticalAlignment(headerRow.getCell(i), "center");
        }
        
        // Header cell content - compact
        XWPFParagraph headerPara1 = headerRow.getCell(0).addParagraph();
        headerPara1.setSpacingAfter(0); // No spacing
        XWPFRun headerRun1 = headerPara1.createRun();
        headerRun1.setText("ITEMS");
        headerRun1.setBold(true);
        headerRun1.setFontSize(9);
        
        XWPFParagraph headerPara2 = headerRow.getCell(1).addParagraph();
        headerPara2.setSpacingAfter(0);
        XWPFRun headerRun2 = headerPara2.createRun();
        headerRun2.setText("QTY.");
        headerRun2.setBold(true);
        headerRun2.setFontSize(9);
        
        XWPFParagraph headerPara3 = headerRow.getCell(2).addParagraph();
        headerPara3.setSpacingAfter(0);
        XWPFRun headerRun3 = headerPara3.createRun();
        headerRun3.setText("MRP");
        headerRun3.setBold(true);
        headerRun3.setFontSize(9);
        
        XWPFParagraph headerPara4 = headerRow.getCell(3).addParagraph();
        headerPara4.setSpacingAfter(0);
        XWPFRun headerRun4 = headerPara4.createRun();
        headerRun4.setText("RATE");
        headerRun4.setBold(true);
        headerRun4.setFontSize(9);
        
        XWPFParagraph headerPara5 = headerRow.getCell(4).addParagraph();
        headerPara5.setAlignment(ParagraphAlignment.RIGHT);
        headerPara5.setSpacingAfter(0);
        XWPFRun headerRun5 = headerPara5.createRun();
        headerRun5.setText("AMOUNT");
        headerRun5.setBold(true);
        headerRun5.setFontSize(9);
        
        // Data row (row 1) - This is the template row that will be repeated
        // CRITICAL: #foreach must be in the FIRST cell, #end in the LAST cell
        // This tells XDocReport to repeat THIS ROW within the SAME TABLE
        XWPFTableRow dataRow = itemsTable.getRow(1);
        dataRow.getCell(0).setWidth("4000");
        dataRow.getCell(1).setWidth("1500");
        dataRow.getCell(2).setWidth("1500");
        dataRow.getCell(3).setWidth("1500");
        dataRow.getCell(4).setWidth("1500");
        
        // Set MINIMAL padding for compact, professional look
        for (int i = 0; i < 5; i++) {
            setCellPadding(dataRow.getCell(i), 50); // Minimal padding (50 twips)
            setVerticalAlignment(dataRow.getCell(i), "center");
        }
        
        // FIRST cell: #foreach marker (invisible) + item name
        XWPFParagraph dataPara1 = dataRow.getCell(0).addParagraph();
        dataPara1.setSpacingAfter(0); // No spacing
        // Put #foreach in first run (invisible)
        XWPFRun foreachRun = dataPara1.createRun();
        foreachRun.setText("#foreach($item in $items)");
        foreachRun.setFontSize(1); // Tiny
        foreachRun.setColor("FFFFFF"); // White (invisible)
        // Item name in same paragraph
        XWPFRun dataRun1 = dataPara1.createRun();
        dataRun1.setText("$item.item_name");
        dataRun1.setFontSize(9);
        
        // Data cell 2: Quantity
        XWPFParagraph dataPara2 = dataRow.getCell(1).addParagraph();
        dataPara2.setSpacingAfter(0);
        XWPFRun dataRun2 = dataPara2.createRun();
        dataRun2.setText("$item.quantity");
        dataRun2.setFontSize(9);
        
        // Data cell 3: MRP
        XWPFParagraph dataPara3 = dataRow.getCell(2).addParagraph();
        dataPara3.setSpacingAfter(0);
        XWPFRun dataRun3 = dataPara3.createRun();
        dataRun3.setText("$item.mrp");
        dataRun3.setFontSize(9);
        
        // Data cell 4: Rate
        XWPFParagraph dataPara4 = dataRow.getCell(3).addParagraph();
        dataPara4.setSpacingAfter(0);
        XWPFRun dataRun4 = dataPara4.createRun();
        dataRun4.setText("$item.rate");
        dataRun4.setFontSize(9);
        
        // LAST cell: Amount + #end marker (invisible)
        XWPFParagraph dataPara5 = dataRow.getCell(4).addParagraph();
        dataPara5.setAlignment(ParagraphAlignment.RIGHT);
        dataPara5.setSpacingAfter(0);
        XWPFRun dataRun5 = dataPara5.createRun();
        dataRun5.setText("$item.amount");
        dataRun5.setFontSize(9);
        // Put #end in same paragraph (invisible)
        XWPFRun endRun = dataPara5.createRun();
        endRun.setText("#end");
        endRun.setFontSize(1); // Tiny
        endRun.setColor("FFFFFF"); // White (invisible)
        
        // Minimal spacing
        XWPFParagraph spacer4 = document.createParagraph();
        spacer4.setSpacingAfter(50);
        
        // ========== SUMMARY SECTION ==========
        // Match sample: SUBTOTAL, Total Amount, Received Amount, Previous Balance, Current Balance
        XWPFTable summaryTable = document.createTable(5, 2);
        summaryTable.setWidth(10000); // 100% in twips
        ensureTableGrid(summaryTable, new long[]{7000, 3000}); // 70%, 30%
        summaryTable.getRow(0).getCell(0).setWidth("7000"); // 70% in twips
        summaryTable.getRow(0).getCell(1).setWidth("3000"); // 30% in twips
        
        // Set MINIMAL padding and alignment for all summary cells - compact layout
        for (int i = 0; i < 5; i++) {
            setCellPadding(summaryTable.getRow(i).getCell(0), 50); // Minimal padding
            setCellPadding(summaryTable.getRow(i).getCell(1), 50);
            setVerticalAlignment(summaryTable.getRow(i).getCell(0), "center");
            setVerticalAlignment(summaryTable.getRow(i).getCell(1), "center");
        }
        
        // Subtotal (aligned with AMOUNT column) - compact
        XWPFParagraph subtotalPara1 = summaryTable.getRow(0).getCell(0).addParagraph();
        subtotalPara1.setSpacingAfter(0); // No spacing
        XWPFRun subtotalRun1 = subtotalPara1.createRun();
        subtotalRun1.setText("SUBTOTAL:");
        subtotalRun1.setBold(true);
        subtotalRun1.setFontSize(9);
        XWPFParagraph subtotalPara2 = summaryTable.getRow(0).getCell(1).addParagraph();
        subtotalPara2.setAlignment(ParagraphAlignment.RIGHT);
        subtotalPara2.setSpacingAfter(0);
        XWPFRun subtotalRun2 = subtotalPara2.createRun();
        subtotalRun2.setText("$subtotal");
        subtotalRun2.setFontSize(9);
        
        // Total Amount - compact
        XWPFParagraph totalPara1 = summaryTable.getRow(1).getCell(0).addParagraph();
        totalPara1.setSpacingAfter(0);
        XWPFRun totalRun1 = totalPara1.createRun();
        totalRun1.setText("Total Amount:");
        totalRun1.setBold(true);
        totalRun1.setFontSize(10);
        XWPFParagraph totalPara2 = summaryTable.getRow(1).getCell(1).addParagraph();
        totalPara2.setAlignment(ParagraphAlignment.RIGHT);
        totalPara2.setSpacingAfter(0);
        XWPFRun totalRun2 = totalPara2.createRun();
        totalRun2.setText("$total_amount");
        totalRun2.setBold(true);
        totalRun2.setFontSize(10);
        
        // Received Amount - compact
        XWPFParagraph receivedPara1 = summaryTable.getRow(2).getCell(0).addParagraph();
        receivedPara1.setSpacingAfter(0);
        XWPFRun receivedRun1 = receivedPara1.createRun();
        receivedRun1.setText("Received Amount:");
        receivedRun1.setFontSize(9);
        XWPFParagraph receivedPara2 = summaryTable.getRow(2).getCell(1).addParagraph();
        receivedPara2.setAlignment(ParagraphAlignment.RIGHT);
        receivedPara2.setSpacingAfter(0);
        XWPFRun receivedRun2 = receivedPara2.createRun();
        receivedRun2.setText("$received_amount");
        receivedRun2.setFontSize(9);
        
        // Previous Balance - compact
        XWPFParagraph prevPara1 = summaryTable.getRow(3).getCell(0).addParagraph();
        prevPara1.setSpacingAfter(0);
        XWPFRun prevRun1 = prevPara1.createRun();
        prevRun1.setText("Previous Balance:");
        prevRun1.setFontSize(9);
        XWPFParagraph prevPara2 = summaryTable.getRow(3).getCell(1).addParagraph();
        prevPara2.setAlignment(ParagraphAlignment.RIGHT);
        prevPara2.setSpacingAfter(0);
        XWPFRun prevRun2 = prevPara2.createRun();
        prevRun2.setText("$previous_balance");
        prevRun2.setFontSize(9);
        
        // Current Balance - compact
        XWPFParagraph currPara1 = summaryTable.getRow(4).getCell(0).addParagraph();
        currPara1.setSpacingAfter(0);
        XWPFRun currRun1 = currPara1.createRun();
        currRun1.setText("Current Balance:");
        currRun1.setFontSize(9);
        XWPFParagraph currPara2 = summaryTable.getRow(4).getCell(1).addParagraph();
        currPara2.setAlignment(ParagraphAlignment.RIGHT);
        currPara2.setSpacingAfter(0);
        XWPFRun currRun2 = currPara2.createRun();
        currRun2.setText("$current_balance");
        currRun2.setFontSize(9);
        
        // Amount in Words - in a separate table row for proper alignment
        XWPFTable wordsTable = document.createTable(1, 1);
        wordsTable.setWidth(10000);
        ensureTableGrid(wordsTable, new long[]{10000});
        setCellPadding(wordsTable.getRow(0).getCell(0), 50);
        setVerticalAlignment(wordsTable.getRow(0).getCell(0), "center");
        XWPFParagraph wordsPara = wordsTable.getRow(0).getCell(0).addParagraph();
        wordsPara.setSpacingAfter(0);
        XWPFRun wordsRun = wordsPara.createRun();
        wordsRun.setText("Amount in Words: $amount_in_words");
        wordsRun.setFontSize(10);
        
        // Minimal spacing
        XWPFParagraph spacer5 = document.createParagraph();
        spacer5.setSpacingAfter(50);
        
        // ========== FOOTER ==========
        // Only show bank details if they exist
        XWPFParagraph footerPara1 = document.createParagraph();
        XWPFRun footerRun1 = footerPara1.createRun();
        footerRun1.setText("#if($bank_details)BANK DETAILS:#end");
        footerRun1.setBold(true);
        footerRun1.setFontSize(10);
        
        XWPFParagraph footerPara2 = document.createParagraph();
        XWPFRun footerRun2 = footerPara2.createRun();
        footerRun2.setText("#if($bank_details)$bank_details#end");
        footerRun2.setFontSize(10);
        
        document.createParagraph();
        
        // Only show terms if they exist
        XWPFParagraph footerPara3 = document.createParagraph();
        XWPFRun footerRun3 = footerPara3.createRun();
        footerRun3.setText("#if($terms_conditions)TERMS AND CONDITIONS:#end");
        footerRun3.setBold(true);
        footerRun3.setFontSize(10);
        
        XWPFParagraph footerPara4 = document.createParagraph();
        XWPFRun footerRun4 = footerPara4.createRun();
        footerRun4.setText("#if($terms_conditions)$terms_conditions#end");
        footerRun4.setFontSize(10);
        
        document.createParagraph();
        
        // Footer slogan - only if exists
        XWPFParagraph footerPara5 = document.createParagraph();
        footerPara5.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun footerRun5 = footerPara5.createRun();
        footerRun5.setText("#if($footer_slogan)$footer_slogan#end");
        footerRun5.setItalic(true);
        footerRun5.setFontSize(10);
        
        // Save document
        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            document.write(out);
        }
        
        document.close();
    }
}


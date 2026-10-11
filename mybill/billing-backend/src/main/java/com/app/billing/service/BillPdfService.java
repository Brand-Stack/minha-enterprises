package com.app.billing.service;

import com.app.billing.dto.InvoiceDto;
import com.app.billing.dto.QuotationDto;
import com.app.billing.dto.ClientDto;
import com.app.billing.model.CompanySettings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating professionally formatted PDFs for Bills, Invoices, Quotations.
 * Uses iText 7 with proper text wrapping and dynamic row heights.
 * 
 * Features:
 * - No text overlapping - all text wraps properly
 * - Dynamic row heights based on content
 * - Professional layout for all bill types
 * - Consistent formatting across Print and PDF Download
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillPdfService {

    private final CompanySettingsService companySettingsService;
    private final ClientService clientService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG_COLOR = new DeviceRgb(240, 240, 240);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(200, 200, 200);
    private static final float PAGE_MARGIN = 36f;

    /**
     * Generate PDF for Quotation with proper text wrapping
     */
    public byte[] generateQuotationPdf(QuotationDto quotation) {
        log.info("Generating PDF for quotation: {}", quotation.getQuotationNumber());
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            // Get company settings
            var companySettings = getCompanySettings();

            // Get party details
            ClientDto partyDto = null;
            if (quotation.getPartyId() != null) {
                try {
                    partyDto = clientService.findById(quotation.getPartyId());
                } catch (Exception e) {
                    log.warn("Failed to load party details: {}", e.getMessage());
                }
            }

            // Add company header
            addCompanyHeader(document, companySettings, boldFont, regularFont);

            // Add document title
            addDocumentTitle(document, "QUOTATION", boldFont);

            // Add bill details section - use "Quotation No:" label
            addBillDetails(document, 
                quotation.getQuotationNumber(),
                quotation.getQuotationDate(),
                null, // Payment status not applicable for quotation
                boldFont, regularFont,
                "Quotation No:");

            // Add party details section
            String partyName = partyDto != null ? partyDto.getPartyName() : quotation.getPartyName();
            String partyPhone = partyDto != null ? partyDto.getPhone() : "";
            String partyAddress = partyDto != null ? buildAddress(partyDto) : "";
            String partyGstin = partyDto != null ? partyDto.getGstin() : "";
            
            addPartyDetails(document, partyName, partyPhone, partyAddress, partyGstin, boldFont, regularFont);

            // Add items table
            addQuotationItemsTable(document, quotation.getItems(), boldFont, regularFont);

            // Add totals section
            addTotalsSection(document, 
                quotation.getSubtotal(),
                quotation.getDiscountPercent(),
                quotation.getDiscountAmount(),
                null, null, // No GST for quotation display
                quotation.getTotalAmount(),
                boldFont, regularFont);

            // Add footer
            addFooter(document, companySettings, regularFont);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating quotation PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate quotation PDF", e);
        }
    }

    /**
     * Generate PDF for Invoice/GST Bill/Estimate with proper text wrapping
     */
    public byte[] generateInvoicePdf(InvoiceDto invoice) {
        log.info("Generating PDF for invoice: {}", invoice.getInvoiceNumber());
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            // Get company settings
            var companySettings = getCompanySettings();

            // Get party details
            ClientDto partyDto = null;
            if (invoice.getPartyId() != null) {
                try {
                    partyDto = clientService.findById(invoice.getPartyId());
                } catch (Exception e) {
                    log.warn("Failed to load party details: {}", e.getMessage());
                }
            }

            // Add company header
            addCompanyHeader(document, companySettings, boldFont, regularFont);

            // Determine document type
            String docType = "INVOICE";
            if (invoice.getBillType() != null) {
                switch (invoice.getBillType()) {
                    case ESTIMATE:
                        docType = "ESTIMATE";
                        break;
                    case GST:
                        docType = "GST INVOICE";
                        break;
                }
            }
            // Add DRAFT prefix if status is DRAFT
            if (invoice.getStatus() != null && 
                invoice.getStatus() == com.app.billing.model.Invoice.BillStatus.DRAFT) {
                docType = "DRAFT - " + docType;
            }

            // Add document title
            addDocumentTitle(document, docType, boldFont);

            // Add bill details section - use appropriate label based on bill type
            String paymentStatus = null;
            if (invoice.getBillType() != com.app.billing.model.Invoice.BillType.ESTIMATE && 
                invoice.getPaymentStatus() != null) {
                paymentStatus = invoice.getPaymentStatus().name();
            }
            
            // Determine bill number label based on type
            String billNumberLabel;
            if (invoice.getBillType() == com.app.billing.model.Invoice.BillType.GST) {
                billNumberLabel = "Invoice No:";
            } else if (invoice.getBillType() == com.app.billing.model.Invoice.BillType.ESTIMATE) {
                billNumberLabel = "Estimate No:";
            } else {
                billNumberLabel = "Bill No:";
            }
            
            addBillDetails(document, 
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                paymentStatus,
                boldFont, regularFont,
                billNumberLabel);

            // Add party details section
            String partyName = partyDto != null ? partyDto.getPartyName() : invoice.getPartyName();
            String partyPhone = partyDto != null ? partyDto.getPhone() : "";
            String partyAddress = partyDto != null ? buildAddress(partyDto) : "";
            String partyGstin = partyDto != null ? partyDto.getGstin() : "";
            
            addPartyDetails(document, partyName, partyPhone, partyAddress, partyGstin, boldFont, regularFont);

            // Add items table
            addInvoiceItemsTable(document, invoice.getItems(), boldFont, regularFont);

            // Add totals section with GST if applicable
            BigDecimal cgst = null, sgst = null;
            if (invoice.getBillType() == com.app.billing.model.Invoice.BillType.GST) {
                cgst = invoice.getCgst();
                sgst = invoice.getSgst();
            }
            addTotalsSection(document, 
                invoice.getSubtotal(),
                invoice.getDiscountPercent(),
                invoice.getDiscountAmount(),
                cgst, sgst,
                invoice.getTotalAmount(),
                boldFont, regularFont);

            // Add amount in words
            if (invoice.getTotalAmount() != null) {
                addAmountInWords(document, invoice.getTotalAmount(), regularFont);
            }

            // Add footer
            addFooter(document, companySettings, regularFont);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating invoice PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    /**
     * Add company header with proper text wrapping
     */
    private void addCompanyHeader(Document document, CompanySettings settings, PdfFont boldFont, PdfFont regularFont) {
        // Company name
        String companyName = settings.getCompanyName() != null ? settings.getCompanyName() : "Company Name";
        Paragraph companyNamePara = new Paragraph(companyName)
                .setFont(boldFont)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(companyNamePara);

        // Company address - with text wrapping
        String address = buildCompanyAddress(settings);
        if (!address.isEmpty()) {
            Paragraph addressPara = new Paragraph(address)
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(3);
            document.add(addressPara);
        }

        // Contact info
        StringBuilder contactInfo = new StringBuilder();
        if (settings.getPhone() != null && !settings.getPhone().isEmpty()) {
            contactInfo.append("Phone: ").append(settings.getPhone());
        }
        if (settings.getEmail() != null && !settings.getEmail().isEmpty()) {
            if (contactInfo.length() > 0) contactInfo.append(" | ");
            contactInfo.append("Email: ").append(settings.getEmail());
        }
        if (settings.getGstin() != null && !settings.getGstin().isEmpty()) {
            if (contactInfo.length() > 0) contactInfo.append(" | ");
            contactInfo.append("GSTIN: ").append(settings.getGstin());
        }
        
        if (contactInfo.length() > 0) {
            Paragraph contactPara = new Paragraph(contactInfo.toString())
                    .setFont(regularFont)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10);
            document.add(contactPara);
        }

        // Separator line
        document.add(new Paragraph()
                .setBorderBottom(new SolidBorder(BORDER_COLOR, 1))
                .setMarginBottom(15));
    }

    /**
     * Add document title
     */
    private void addDocumentTitle(Document document, String title, PdfFont boldFont) {
        Paragraph titlePara = new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(15);
        document.add(titlePara);
    }

    /**
     * Add bill details section with proper layout
     * 
     * @param billNumberLabel Custom label for bill number (e.g., "Quotation No:", "Invoice No:", "Bill No:")
     */
    private void addBillDetails(Document document, String billNumber, LocalDate billDate, 
                                String paymentStatus, PdfFont boldFont, PdfFont regularFont,
                                String billNumberLabel) {
        // Create a 2-column table for bill details
        Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(15);

        // Bill Number - use custom label if provided
        String label = (billNumberLabel != null && !billNumberLabel.isEmpty()) ? billNumberLabel : "Bill No:";
        Cell labelCell = createLabelCell(label, boldFont);
        Cell valueCell = createValueCell(billNumber != null ? billNumber : "N/A", regularFont);
        detailsTable.addCell(labelCell);
        detailsTable.addCell(valueCell);

        // Bill Date
        labelCell = createLabelCell("Date:", boldFont);
        valueCell = createValueCell(billDate != null ? billDate.format(DATE_FORMATTER) : "N/A", regularFont);
        detailsTable.addCell(labelCell);
        detailsTable.addCell(valueCell);

        // Payment Status (if applicable)
        if (paymentStatus != null) {
            labelCell = createLabelCell("Payment Status:", boldFont);
            valueCell = createValueCell(paymentStatus, regularFont);
            detailsTable.addCell(labelCell);
            detailsTable.addCell(valueCell);
        }

        document.add(detailsTable);
    }

    /**
     * Add party details section with proper text wrapping for long names and addresses
     */
    private void addPartyDetails(Document document, String partyName, String phone, 
                                 String address, String gstin, PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Paragraph header = new Paragraph("Party Details")
                .setFont(boldFont)
                .setFontSize(11)
                .setMarginBottom(5);
        document.add(header);

        // Create bordered container for party details
        Table partyTable = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setMarginBottom(15);

        // Party Name - allow text wrapping for long names
        if (partyName != null && !partyName.isEmpty()) {
            Cell nameCell = new Cell()
                    .add(new Paragraph("Name: " + partyName)
                            .setFont(regularFont)
                            .setFontSize(10))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(5);
            partyTable.addCell(nameCell);
        }

        // Phone
        if (phone != null && !phone.isEmpty()) {
            Cell phoneCell = new Cell()
                    .add(new Paragraph("Phone: " + phone)
                            .setFont(regularFont)
                            .setFontSize(10))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(5)
                    .setPaddingTop(0);
            partyTable.addCell(phoneCell);
        }

        // Address - allow text wrapping for long addresses
        if (address != null && !address.isEmpty()) {
            Cell addressCell = new Cell()
                    .add(new Paragraph("Address: " + address)
                            .setFont(regularFont)
                            .setFontSize(10))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(5)
                    .setPaddingTop(0);
            partyTable.addCell(addressCell);
        }

        // GSTIN
        if (gstin != null && !gstin.isEmpty()) {
            Cell gstinCell = new Cell()
                    .add(new Paragraph("GSTIN: " + gstin)
                            .setFont(regularFont)
                            .setFontSize(10))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(5)
                    .setPaddingTop(0);
            partyTable.addCell(gstinCell);
        }

        document.add(partyTable);
    }

    /**
     * Add items table for quotation with proper text wrapping
     */
    private void addQuotationItemsTable(Document document, List<QuotationDto.QuotationItemDto> items,
                                        PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Paragraph header = new Paragraph("Items")
                .setFont(boldFont)
                .setFontSize(11)
                .setMarginBottom(5);
        document.add(header);

        // Create table with percentage-based column widths for proper wrapping
        // Item Name | Category | EN Code | Qty | Unit | Rate | Tax | Amount
        Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{20, 12, 12, 8, 8, 12, 10, 18}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(15);

        // Header row
        addTableHeaderCell(itemsTable, "Item Name", boldFont);
        addTableHeaderCell(itemsTable, "Category", boldFont);
        addTableHeaderCell(itemsTable, "EN Code", boldFont);
        addTableHeaderCell(itemsTable, "Qty", boldFont);
        addTableHeaderCell(itemsTable, "Unit", boldFont);
        addTableHeaderCell(itemsTable, "Rate", boldFont);
        addTableHeaderCell(itemsTable, "Tax", boldFont);
        addTableHeaderCell(itemsTable, "Amount", boldFont);

        // Data rows
        if (items != null && !items.isEmpty()) {
            for (QuotationDto.QuotationItemDto item : items) {
                // Item Name - with text wrapping
                addTableDataCell(itemsTable, 
                    item.getItemName() != null ? item.getItemName() : "", regularFont, TextAlignment.LEFT);
                
                // Category
                addTableDataCell(itemsTable, "", regularFont, TextAlignment.LEFT);
                
                // EN Code - with text wrapping
                addTableDataCell(itemsTable, 
                    item.getItemCode() != null ? item.getItemCode() : "", regularFont, TextAlignment.LEFT);
                
                // Quantity
                addTableDataCell(itemsTable, 
                    item.getQuantity() != null ? formatDecimal(item.getQuantity()) : "0", 
                    regularFont, TextAlignment.CENTER);
                
                // Unit
                addTableDataCell(itemsTable, "", regularFont, TextAlignment.CENTER);
                
                // Rate
                addTableDataCell(itemsTable, 
                    item.getUnitPrice() != null ? formatCurrency(item.getUnitPrice()) : "0.00", 
                    regularFont, TextAlignment.RIGHT);
                
                // Tax
                addTableDataCell(itemsTable, "0.00", regularFont, TextAlignment.RIGHT);
                
                // Amount - with text wrapping for large amounts
                addTableDataCell(itemsTable, 
                    item.getTotalAmount() != null ? formatCurrency(item.getTotalAmount()) : "0.00", 
                    regularFont, TextAlignment.RIGHT);
            }
        } else {
            // Empty row if no items
            Cell emptyCell = new Cell(1, 8)
                    .add(new Paragraph("No items")
                            .setFont(regularFont)
                            .setFontSize(10)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setPadding(10);
            itemsTable.addCell(emptyCell);
        }

        document.add(itemsTable);
    }

    /**
     * Add items table for invoice with proper text wrapping
     */
    private void addInvoiceItemsTable(Document document, List<InvoiceDto.InvoiceItemDto> items,
                                      PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Paragraph header = new Paragraph("Items")
                .setFont(boldFont)
                .setFontSize(11)
                .setMarginBottom(5);
        document.add(header);

        // Create table with percentage-based column widths
        // Item Name | Item Code | Qty | Rate | Amount
        Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{35, 15, 12, 18, 20}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(15);

        // Header row
        addTableHeaderCell(itemsTable, "Item Name", boldFont);
        addTableHeaderCell(itemsTable, "Item Code", boldFont);
        addTableHeaderCell(itemsTable, "Qty", boldFont);
        addTableHeaderCell(itemsTable, "Rate", boldFont);
        addTableHeaderCell(itemsTable, "Amount", boldFont);

        // Data rows
        if (items != null && !items.isEmpty()) {
            for (InvoiceDto.InvoiceItemDto item : items) {
                // Item Name - with text wrapping for long names
                addTableDataCell(itemsTable, 
                    item.getItemName() != null ? item.getItemName() : "", regularFont, TextAlignment.LEFT);
                
                // Item Code - with text wrapping
                addTableDataCell(itemsTable, 
                    item.getItemCode() != null ? item.getItemCode() : "", regularFont, TextAlignment.LEFT);
                
                // Quantity
                addTableDataCell(itemsTable, 
                    item.getQuantity() != null ? formatDecimal(item.getQuantity()) : "0", 
                    regularFont, TextAlignment.CENTER);
                
                // Rate
                addTableDataCell(itemsTable, 
                    item.getUnitPrice() != null ? formatCurrency(item.getUnitPrice()) : "0.00", 
                    regularFont, TextAlignment.RIGHT);
                
                // Amount
                addTableDataCell(itemsTable, 
                    item.getTotalAmount() != null ? formatCurrency(item.getTotalAmount()) : "0.00", 
                    regularFont, TextAlignment.RIGHT);
            }
        } else {
            // Empty row if no items
            Cell emptyCell = new Cell(1, 5)
                    .add(new Paragraph("No items")
                            .setFont(regularFont)
                            .setFontSize(10)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setPadding(10);
            itemsTable.addCell(emptyCell);
        }

        document.add(itemsTable);
    }

    /**
     * Add totals section
     */
    private void addTotalsSection(Document document, BigDecimal subtotal, BigDecimal discountPercent,
                                  BigDecimal discountAmount, BigDecimal cgst, BigDecimal sgst,
                                  BigDecimal totalAmount, PdfFont boldFont, PdfFont regularFont) {
        // Create right-aligned totals table
        Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(50))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                .setMarginTop(10)
                .setMarginBottom(15);

        // Subtotal
        addTotalsRow(totalsTable, "Subtotal:", formatCurrency(subtotal), regularFont, false);

        // Discount
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            String discountLabel = "Discount";
            if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
                discountLabel += " (" + formatDecimal(discountPercent) + "%)";
            }
            discountLabel += ":";
            addTotalsRow(totalsTable, discountLabel, formatCurrency(discountAmount), regularFont, false);
        }

        // CGST
        if (cgst != null && cgst.compareTo(BigDecimal.ZERO) > 0) {
            addTotalsRow(totalsTable, "CGST:", formatCurrency(cgst), regularFont, false);
        }

        // SGST
        if (sgst != null && sgst.compareTo(BigDecimal.ZERO) > 0) {
            addTotalsRow(totalsTable, "SGST:", formatCurrency(sgst), regularFont, false);
        }

        // Total Amount (bold)
        addTotalsRow(totalsTable, "Total Amount:", formatCurrency(totalAmount), boldFont, true);

        document.add(totalsTable);
    }

    /**
     * Add amount in words
     */
    private void addAmountInWords(Document document, BigDecimal amount, PdfFont regularFont) {
        String amountInWords = convertNumberToWords(amount);
        Paragraph wordsPara = new Paragraph("Amount in Words: " + amountInWords)
                .setFont(regularFont)
                .setFontSize(10)
                .setItalic()
                .setMarginBottom(15);
        document.add(wordsPara);
    }

    /**
     * Add footer
     */
    private void addFooter(Document document, CompanySettings settings, PdfFont regularFont) {
        // Separator line
        document.add(new Paragraph()
                .setBorderTop(new SolidBorder(BORDER_COLOR, 1))
                .setMarginTop(20)
                .setMarginBottom(10));

        // Generated date
        String generatedDate = "Generated on: " + LocalDate.now().format(DATE_FORMATTER);
        Paragraph datePara = new Paragraph(generatedDate)
                .setFont(regularFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(5);
        document.add(datePara);

        // Footer slogan
        String footerSlogan = settings.getFooterSlogan() != null && !settings.getFooterSlogan().isEmpty()
                ? settings.getFooterSlogan() : "Thank you for your business!";
        Paragraph footerPara = new Paragraph(footerSlogan)
                .setFont(regularFont)
                .setFontSize(10)
                .setItalic()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footerPara);
    }

    // ==================== Helper Methods ====================

    private Cell createLabelCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text)
                        .setFont(font)
                        .setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setPadding(3);
    }

    private Cell createValueCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text)
                        .setFont(font)
                        .setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setPadding(3);
    }

    private void addTableHeaderCell(Table table, String text, PdfFont font) {
        Cell cell = new Cell()
                .add(new Paragraph(text)
                        .setFont(font)
                        .setFontSize(9))
                .setBackgroundColor(HEADER_BG_COLOR)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(5)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        table.addHeaderCell(cell);
    }

    private void addTableDataCell(Table table, String text, PdfFont font, TextAlignment alignment) {
        // Create cell with proper text wrapping - no fixed height
        Cell cell = new Cell()
                .add(new Paragraph(text != null ? text : "")
                        .setFont(font)
                        .setFontSize(9)
                        .setTextAlignment(alignment))
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(5)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        // Do NOT set fixed height - let cell expand based on content
        table.addCell(cell);
    }

    private void addTotalsRow(Table table, String label, String value, PdfFont font, boolean isBold) {
        Cell labelCell = new Cell()
                .add(new Paragraph(label)
                        .setFont(font)
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.LEFT))
                .setBorder(isBold ? new SolidBorder(BORDER_COLOR, 0.5f) : Border.NO_BORDER)
                .setPadding(3);
        
        Cell valueCell = new Cell()
                .add(new Paragraph(value)
                        .setFont(font)
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(isBold ? new SolidBorder(BORDER_COLOR, 0.5f) : Border.NO_BORDER)
                .setPadding(3);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private CompanySettings getCompanySettings() {
        try {
            var settingsDto = companySettingsService.getSettings();
            return CompanySettings.builder()
                    .companyName(settingsDto.getCompanyName())
                    .address(settingsDto.getAddress())
                    .city(settingsDto.getCity())
                    .state(settingsDto.getState())
                    .pincode(settingsDto.getPincode())
                    .phone(settingsDto.getPhone())
                    .email(settingsDto.getEmail())
                    .gstin(settingsDto.getGstin())
                    .footerSlogan(settingsDto.getFooterSlogan())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to load company settings: {}", e.getMessage());
            return CompanySettings.builder().build();
        }
    }

    private String buildCompanyAddress(CompanySettings settings) {
        StringBuilder sb = new StringBuilder();
        if (settings.getAddress() != null && !settings.getAddress().isEmpty()) {
            sb.append(settings.getAddress());
        }
        if (settings.getCity() != null && !settings.getCity().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(settings.getCity());
        }
        if (settings.getState() != null && !settings.getState().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(settings.getState());
        }
        if (settings.getPincode() != null && !settings.getPincode().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(settings.getPincode());
        }
        return sb.toString();
    }

    private String buildAddress(ClientDto party) {
        StringBuilder sb = new StringBuilder();
        if (party.getAddress() != null && !party.getAddress().isEmpty()) {
            sb.append(party.getAddress());
        }
        if (party.getCity() != null && !party.getCity().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(party.getCity());
        }
        if (party.getState() != null && !party.getState().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(party.getState());
        }
        if (party.getPincode() != null && !party.getPincode().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(party.getPincode());
        }
        return sb.toString();
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "0.00";
        return String.format("%.2f", value.doubleValue());
    }

    /**
     * Convert number to words (Indian numbering system)
     */
    private String convertNumberToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "Zero Rupees Only";
        }
        
        try {
            long rupees = amount.longValue();
            long paise = amount.remainder(BigDecimal.ONE).multiply(new BigDecimal("100")).longValue();
            
            String rupeesWords = numberToWords(rupees);
            String paiseWords = paise > 0 ? numberToWords(paise) : "";
            
            StringBuilder result = new StringBuilder();
            if (rupees > 0) {
                result.append(rupeesWords).append(" Rupees");
            }
            if (paise > 0) {
                if (result.length() > 0) result.append(" and ");
                result.append(paiseWords).append(" Paise");
            }
            if (result.length() == 0) {
                result.append("Zero Rupees");
            }
            result.append(" Only");
            
            return result.toString();
        } catch (Exception e) {
            return "Amount in Words";
        }
    }

    private String numberToWords(long number) {
        if (number == 0) return "Zero";
        
        String[] ones = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
                "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        
        if (number < 20) {
            return ones[(int) number];
        }
        
        if (number < 100) {
            return tens[(int) (number / 10)] + (number % 10 != 0 ? " " + ones[(int) (number % 10)] : "");
        }
        
        if (number < 1000) {
            return ones[(int) (number / 100)] + " Hundred" + (number % 100 != 0 ? " " + numberToWords(number % 100) : "");
        }
        
        if (number < 100000) {
            return numberToWords(number / 1000) + " Thousand" + (number % 1000 != 0 ? " " + numberToWords(number % 1000) : "");
        }
        
        if (number < 10000000) {
            return numberToWords(number / 100000) + " Lakh" + (number % 100000 != 0 ? " " + numberToWords(number % 100000) : "");
        }
        
        return numberToWords(number / 10000000) + " Crore" + (number % 10000000 != 0 ? " " + numberToWords(number % 10000000) : "");
    }
}


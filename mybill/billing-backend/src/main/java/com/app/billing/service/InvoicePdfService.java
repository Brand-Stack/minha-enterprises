package com.app.billing.service;

import com.app.billing.dto.InvoiceDto;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {
    
    private final CompanySettingsService companySettingsService;
    
    @Value("${invoice.print.footer.slogan:Thank you for your business!}")
    private String defaultFooterSlogan;
    
    public byte[] generatePdf(InvoiceDto invoice) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);

            // Add a thin, professional border round the Document
            pdf.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE, new com.itextpdf.kernel.events.IEventHandler() {
                @Override
                public void handleEvent(com.itextpdf.kernel.events.Event event) {
                    com.itextpdf.kernel.events.PdfDocumentEvent docEvent = (com.itextpdf.kernel.events.PdfDocumentEvent) event;
                    com.itextpdf.kernel.pdf.PdfPage page = docEvent.getPage();
                    com.itextpdf.kernel.geom.Rectangle pageSize = page.getPageSize();
                    com.itextpdf.kernel.pdf.canvas.PdfCanvas canvas = new com.itextpdf.kernel.pdf.canvas.PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
                    canvas.setStrokeColor(new com.itextpdf.kernel.colors.DeviceRgb(150, 150, 150))
                          .setLineWidth(1f)
                          .rectangle(15f, 15f, pageSize.getWidth() - 30f, pageSize.getHeight() - 30f)
                          .stroke();
                }
            });

            Document document = new Document(pdf);
            document.setMargins(35f, 35f, 35f, 35f);
            
            // Get company settings
            var companySettings = companySettingsService.getSettings();
            String companyName = companySettings.getCompanyName() != null && !companySettings.getCompanyName().isEmpty() 
                    ? companySettings.getCompanyName() : "COMPANY NAME";
            String footerSlogan = companySettings.getFooterSlogan() != null && !companySettings.getFooterSlogan().isEmpty()
                    ? companySettings.getFooterSlogan() : defaultFooterSlogan;
            
            // Company Header Logo Replacement
            try {
                com.itextpdf.io.image.ImageData logoData = com.itextpdf.io.image.ImageDataFactory.create(new org.springframework.core.io.ClassPathResource("images/minhaEnterprisesFullLogo.jpeg").getURL());
                com.itextpdf.layout.element.Image logo = new com.itextpdf.layout.element.Image(logoData);
                logo.scaleToFit(220, 80);
                logo.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                logo.setMarginBottom(10);
                document.add(logo);
            } catch (Exception e) {
                // Fallback text
                if (companySettings.getCompanyName() != null && !companySettings.getCompanyName().isEmpty()) {
                    Paragraph companyHeader = new Paragraph(companyName)
                            .setFontSize(20)
                            .setBold()
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(10);
                    document.add(companyHeader);
                }
            }

            // Company Address
            if (companySettings.getAddress() != null && !companySettings.getAddress().isEmpty()) {
                String addressLine = companySettings.getAddress();
                if (companySettings.getCity() != null && !companySettings.getCity().isEmpty()) {
                    addressLine += ", " + companySettings.getCity();
                }
                if (companySettings.getState() != null && !companySettings.getState().isEmpty()) {
                    addressLine += ", " + companySettings.getState();
                }
                if (companySettings.getPincode() != null && !companySettings.getPincode().isEmpty()) {
                    addressLine += " - " + companySettings.getPincode();
                }
                Paragraph address = new Paragraph(addressLine)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(5);
                document.add(address);
            }
                
            // Company Contact
            String contactInfo = "";
            if (companySettings.getPhone() != null && !companySettings.getPhone().isEmpty()) {
                contactInfo = "Phone: " + companySettings.getPhone();
            }
            if (companySettings.getEmail() != null && !companySettings.getEmail().isEmpty()) {
                if (!contactInfo.isEmpty()) contactInfo += " | ";
                contactInfo += "Email: " + companySettings.getEmail();
            }
            if (!contactInfo.isEmpty()) {
                Paragraph contact = new Paragraph(contactInfo)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(15);
                document.add(contact);
            }
            
            // Invoice Header
            Paragraph header = new Paragraph("INVOICE")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(header);
            
            // Invoice Details - Show invoice number for all bills (including Estimate with EST-XXX prefix)
            if (invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isEmpty()) {
                Paragraph invoiceNumber = new Paragraph("Invoice Number: " + invoice.getInvoiceNumber())
                        .setMarginBottom(5);
                document.add(invoiceNumber);
            }
            
            // Show only date (no time)
            String dateStr = invoice.getInvoiceDate() != null 
                    ? invoice.getInvoiceDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    : "N/A";
            Paragraph invoiceDate = new Paragraph("Date: " + dateStr)
                    .setMarginBottom(5);
            document.add(invoiceDate);
            
            // Hide payment status for Estimate bills
            if (invoice.getPaymentStatus() != null &&
                (invoice.getBillType() == null || invoice.getBillType() != com.app.billing.model.Invoice.BillType.ESTIMATE)) {
                Paragraph paymentStatus = new Paragraph("Payment Status: " + invoice.getPaymentStatus().toString())
                        .setMarginBottom(20);
                document.add(paymentStatus);
            } else if (invoice.getBillType() == null || invoice.getBillType() != com.app.billing.model.Invoice.BillType.ESTIMATE) {
                // Add spacing if payment status is not shown
                document.add(new Paragraph().setMarginBottom(20));
            }
            
            // Party Details
            Paragraph partyHeader = new Paragraph("Bill To:")
                    .setBold()
                    .setMarginBottom(5);
            document.add(partyHeader);
            
            String partyName = invoice.getPartyName() != null && !invoice.getPartyName().isEmpty() 
                    ? invoice.getPartyName() : "N/A";
            Paragraph partyNamePara = new Paragraph(partyName)
                    .setMarginBottom(10);
            document.add(partyNamePara);
            
            // Items Table - Improved formatting
            float[] columnWidths = {4, 1.5f, 1.5f, 1.5f, 1.5f};
            Table table = new Table(columnWidths);
            table.setWidth(100); // Full width percentage
            table.setMarginTop(10);
            table.setMarginBottom(10);
            
            // Table Header with better styling
            table.addHeaderCell(new Paragraph("Item Name").setBold().setPadding(8));
            table.addHeaderCell(new Paragraph("Qty").setBold().setPadding(8).setTextAlignment(TextAlignment.CENTER));
            table.addHeaderCell(new Paragraph("Unit Price").setBold().setPadding(8).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(new Paragraph("Tax").setBold().setPadding(8).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(new Paragraph("Total").setBold().setPadding(8).setTextAlignment(TextAlignment.RIGHT));
            
            // Table Rows with better formatting
            if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
                for (InvoiceDto.InvoiceItemDto item : invoice.getItems()) {
                    table.addCell(new Paragraph(item.getItemName() != null ? item.getItemName() : "").setPadding(6));
                    table.addCell(new Paragraph(item.getQuantity() != null ? item.getQuantity().toString() : "0")
                            .setTextAlignment(TextAlignment.CENTER).setPadding(6));
                    table.addCell(new Paragraph(item.getUnitPrice() != null ? "₹" + item.getUnitPrice().toString() : "₹0")
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                    table.addCell(new Paragraph(item.getTaxAmount() != null ? "₹" + item.getTaxAmount().toString() : "₹0")
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                    table.addCell(new Paragraph(item.getTotalAmount() != null ? "₹" + item.getTotalAmount().toString() : "₹0")
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                }
            }
            
            document.add(table);
            
            // Totals Section - Improved formatting with better spacing
            Paragraph spacer = new Paragraph().setMarginTop(15);
            document.add(spacer);
            
            // Create a right-aligned totals table for better alignment
            float[] totalsWidths = {3, 2};
            Table totalsTable = new Table(totalsWidths);
            totalsTable.setWidth(50); // 50% width, right-aligned
            totalsTable.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
            
            totalsTable.addCell(new Paragraph("Subtotal:").setTextAlignment(TextAlignment.LEFT));
            totalsTable.addCell(new Paragraph("₹" + (invoice.getSubtotal() != null ? invoice.getSubtotal().toString() : "0"))
                    .setTextAlignment(TextAlignment.RIGHT));
            
            if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                String discountText = "Discount";
                if (invoice.getDiscountPercent() != null && invoice.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    discountText += " (" + invoice.getDiscountPercent() + "%)";
                }
                totalsTable.addCell(new Paragraph(discountText).setTextAlignment(TextAlignment.LEFT));
                totalsTable.addCell(new Paragraph("₹" + invoice.getDiscountAmount().toString())
                        .setTextAlignment(TextAlignment.RIGHT));
            }
            
            // Show CGST and SGST for GST bills
            if (invoice.getBillType() != null && invoice.getBillType() == com.app.billing.model.Invoice.BillType.GST) {
                if (invoice.getCgst() != null && invoice.getCgst().compareTo(BigDecimal.ZERO) > 0) {
                    totalsTable.addCell(new Paragraph("CGST:").setTextAlignment(TextAlignment.LEFT));
                    totalsTable.addCell(new Paragraph("₹" + invoice.getCgst().toString())
                            .setTextAlignment(TextAlignment.RIGHT));
                }
                if (invoice.getSgst() != null && invoice.getSgst().compareTo(BigDecimal.ZERO) > 0) {
                    totalsTable.addCell(new Paragraph("SGST:").setTextAlignment(TextAlignment.LEFT));
                    totalsTable.addCell(new Paragraph("₹" + invoice.getSgst().toString())
                            .setTextAlignment(TextAlignment.RIGHT));
                }
            }
            
            // Total row with border
            totalsTable.addCell(new Paragraph("Total Amount:").setBold().setTextAlignment(TextAlignment.LEFT));
            totalsTable.addCell(new Paragraph("₹" + (invoice.getTotalAmount() != null ? invoice.getTotalAmount().toString() : "0"))
                    .setBold().setTextAlignment(TextAlignment.RIGHT));
            
            document.add(totalsTable);
            
            // Footer slogan/quote - Absolute Bottom
            String footerText = footerSlogan != null && !footerSlogan.isEmpty() 
                    ? footerSlogan : defaultFooterSlogan;
            Paragraph footer = new Paragraph(footerText)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setItalic()
                    .setFixedPosition(35f, 25f, com.itextpdf.kernel.geom.PageSize.A4.getWidth() - 70f);
            document.add(footer);
            
            document.close();
            
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
        
        return baos.toByteArray();
    }
}


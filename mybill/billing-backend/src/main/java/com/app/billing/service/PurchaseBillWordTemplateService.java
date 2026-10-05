package com.app.billing.service;

import com.app.billing.dto.PurchaseBillDto;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseBillWordTemplateService {
    
    private final CompanySettingsService companySettingsService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    
    public byte[] generatePurchaseBillPdf(PurchaseBillDto bill) {
        try {
            log.info("Generating purchase bill PDF for bill: {}", bill.getBillNumber());
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            
            // Get company settings
            var companySettings = companySettingsService.getSettings();
            String companyName = companySettings != null && companySettings.getCompanyName() != null && !companySettings.getCompanyName().isEmpty() 
                    ? companySettings.getCompanyName() : "COMPANY NAME";
            
            // Company Header
            if (companySettings != null && companySettings.getCompanyName() != null && !companySettings.getCompanyName().isEmpty()) {
                Paragraph companyHeader = new Paragraph(companyName)
                        .setFontSize(20)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(10);
                document.add(companyHeader);
                
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
                            .setMarginBottom(10);
                    document.add(contact);
                }
            }
            
            // Purchase Bill Header
            Paragraph header = new Paragraph("PURCHASE BILL")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(header);
            
            // Bill Details
            if (bill.getBillNumber() != null && !bill.getBillNumber().isEmpty()) {
                Paragraph billNumber = new Paragraph("Bill Number: " + bill.getBillNumber())
                        .setMarginBottom(5);
                document.add(billNumber);
            }
            
            String dateStr = bill.getBillDate() != null 
                    ? bill.getBillDate().format(DATE_FORMATTER)
                    : "N/A";
            Paragraph billDate = new Paragraph("Date: " + dateStr)
                    .setMarginBottom(5);
            document.add(billDate);
            
            if (bill.getPaymentType() != null) {
                Paragraph paymentType = new Paragraph("Payment Type: " + bill.getPaymentType().name())
                        .setMarginBottom(20);
                document.add(paymentType);
            } else {
                document.add(new Paragraph().setMarginBottom(20));
            }
            
            // Party Details
            Paragraph partyHeader = new Paragraph("Supplier:")
                    .setBold()
                    .setMarginBottom(5);
            document.add(partyHeader);
            
            String partyName = bill.getPartyName() != null && !bill.getPartyName().isEmpty() 
                    ? bill.getPartyName() : "N/A";
            Paragraph partyNamePara = new Paragraph(partyName)
                    .setMarginBottom(5);
            document.add(partyNamePara);
            
            if (bill.getPartyPhone() != null && !bill.getPartyPhone().isEmpty()) {
                Paragraph partyPhone = new Paragraph("Phone: " + bill.getPartyPhone())
                        .setMarginBottom(10);
                document.add(partyPhone);
            }
            
            // Items Table
            float[] columnWidths = {3, 1.5f, 1.5f, 1.5f, 1.5f, 1f};
            Table table = new Table(columnWidths);
            table.setWidth(100);
            table.setMarginTop(10);
            table.setMarginBottom(10);
            
            // Table Header
            table.addHeaderCell(new Paragraph("Item Name").setBold().setPadding(8));
            table.addHeaderCell(new Paragraph("Qty").setBold().setPadding(8).setTextAlignment(TextAlignment.CENTER));
            table.addHeaderCell(new Paragraph("Unit").setBold().setPadding(8).setTextAlignment(TextAlignment.CENTER));
            table.addHeaderCell(new Paragraph("Price").setBold().setPadding(8).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(new Paragraph("Tax %").setBold().setPadding(8).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(new Paragraph("Amount").setBold().setPadding(8).setTextAlignment(TextAlignment.RIGHT));
            
            // Table Rows
            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                for (PurchaseBillDto.PurchaseItemDto item : bill.getItems()) {
                    table.addCell(new Paragraph(item.getItemName() != null ? item.getItemName() : "").setPadding(6));
                    table.addCell(new Paragraph(item.getQuantity() != null ? item.getQuantity().toString() : "0")
                            .setTextAlignment(TextAlignment.CENTER).setPadding(6));
                    table.addCell(new Paragraph(item.getUnit() != null && !item.getUnit().equals("NONE") ? item.getUnit() : "-")
                            .setTextAlignment(TextAlignment.CENTER).setPadding(6));
                    table.addCell(new Paragraph(formatCurrency(item.getPrice()))
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                    table.addCell(new Paragraph(item.getTaxPercent() != null ? item.getTaxPercent().toString() + "%" : "-")
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                    table.addCell(new Paragraph(formatCurrency(item.getAmount()))
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                }
            }
            
            document.add(table);
            
            // Totals
            Paragraph totalsHeader = new Paragraph("TOTALS")
                    .setBold()
                    .setMarginTop(20)
                    .setMarginBottom(10);
            document.add(totalsHeader);
            
            if (bill.getSubtotal() != null) {
                Paragraph subtotal = new Paragraph("Subtotal: " + formatCurrency(bill.getSubtotal()))
                        .setMarginBottom(5);
                document.add(subtotal);
            }
            
            if (bill.getTaxAmount() != null && bill.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                Paragraph tax = new Paragraph("Tax Amount: " + formatCurrency(bill.getTaxAmount()))
                        .setMarginBottom(5);
                document.add(tax);
            }
            
            if (bill.getTotalAmount() != null) {
                Paragraph total = new Paragraph("Total Amount: " + formatCurrency(bill.getTotalAmount()))
                        .setBold()
                        .setFontSize(14)
                        .setMarginTop(10)
                        .setMarginBottom(20);
                document.add(total);
            }
            
            // Notes
            if (bill.getNotes() != null && !bill.getNotes().trim().isEmpty()) {
                Paragraph notesHeader = new Paragraph("Notes:")
                        .setBold()
                        .setMarginTop(20);
                document.add(notesHeader);
                Paragraph notes = new Paragraph(bill.getNotes())
                        .setMarginBottom(20);
                document.add(notes);
            }
            
            // Footer
            if (companySettings != null && companySettings.getFooterSlogan() != null && !companySettings.getFooterSlogan().isEmpty()) {
                Paragraph footer = new Paragraph(companySettings.getFooterSlogan())
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(30)
                        .setItalic();
                document.add(footer);
            }
            
            document.close();
            
            log.info("Purchase bill PDF generated successfully for bill: {}", bill.getBillNumber());
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("CRITICAL ERROR generating purchase bill PDF for bill ID {}: {}", bill.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate purchase bill PDF: " + e.getMessage(), e);
        }
    }
    
    private String formatCurrency(BigDecimal value) {
        if (value == null) return "₹ 0.00";
        return "₹ " + value.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }
}


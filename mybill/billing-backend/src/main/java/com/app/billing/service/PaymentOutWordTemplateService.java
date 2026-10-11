package com.app.billing.service;

import com.app.billing.dto.PaymentOutDto;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutWordTemplateService {
    
    private final CompanySettingsService companySettingsService;
    
    private static final String TEMPLATE_PATH = "templates/payment/PaymentOutTemplate.docx";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    
    public byte[] generatePaymentOutPdf(PaymentOutDto payment) {
        try {
            log.info("Generating payment-out PDF for receipt: {}", payment.getReceiptNumber());
            
            // Load template
            ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
            if (!resource.exists()) {
                log.warn("Template not found at {}, generating default template", TEMPLATE_PATH);
                generateDefaultTemplate();
            }
            
            InputStream templateStream = resource.getInputStream();
            IXDocReport report = XDocReportRegistry.getRegistry().loadReport(templateStream, TemplateEngineKind.Velocity);
            
            // Populate context
            IContext context = report.createContext();
            populateContext(context, payment);
            
            // Process template to generate Word document
            ByteArrayOutputStream docxOutputStream = new ByteArrayOutputStream();
            report.process(context, docxOutputStream);
            
            // Convert Word to PDF
            java.io.ByteArrayInputStream docxInputStream = new java.io.ByteArrayInputStream(docxOutputStream.toByteArray());
            XWPFDocument xwpfDocument = new XWPFDocument(docxInputStream);
            
            ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
            PdfOptions options = null;
            PdfConverter.getInstance().convert(xwpfDocument, pdfStream, options);
            
            xwpfDocument.close();
            docxInputStream.close();
            templateStream.close();
            
            log.info("Payment-out PDF generated successfully for receipt: {}", payment.getReceiptNumber());
            return pdfStream.toByteArray();
            
        } catch (Exception e) {
            log.error("CRITICAL ERROR generating payment-out PDF for ID {}: {}", payment.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate payment-out PDF: " + e.getMessage(), e);
        }
    }
    
    private void populateContext(IContext context, PaymentOutDto payment) {
        // Company settings
        try {
            var companySettings = companySettingsService.getSettings();
            if (companySettings != null) {
                context.put("company_name", getValueOrDefault(companySettings.getCompanyName(), ""));
                context.put("company_address", getValueOrDefault(companySettings.getAddress(), ""));
                context.put("company_city", getValueOrDefault(companySettings.getCity(), ""));
                context.put("company_state", getValueOrDefault(companySettings.getState(), ""));
                context.put("company_pincode", getValueOrDefault(companySettings.getPincode(), ""));
                context.put("company_phone", getValueOrDefault(companySettings.getPhone(), ""));
                context.put("company_email", getValueOrDefault(companySettings.getEmail(), ""));
                context.put("company_gstin", getValueOrDefault(companySettings.getGstin(), ""));
            } else {
                setDefaultCompanySettings(context);
            }
        } catch (Exception e) {
            log.warn("Failed to load company settings, using defaults: {}", e.getMessage());
            setDefaultCompanySettings(context);
        }
        
        // Payment information
        context.put("receipt_number", getValueOrDefault(payment.getReceiptNumber(), ""));
        context.put("date", payment.getDate() != null ? payment.getDate().format(DATE_FORMATTER) : "");
        context.put("party_name", getValueOrDefault(payment.getPartyName(), ""));
        context.put("payment_type", payment.getPaymentType() != null ? payment.getPaymentType().name() : "");
        context.put("paid_amount", formatCurrency(payment.getPaidAmount()));
        context.put("description", getValueOrDefault(payment.getDescription(), ""));
        context.put("reference_number", getValueOrDefault(payment.getReferenceNumber(), ""));
    }
    
    private String getValueOrDefault(String value, String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
    
    private String formatCurrency(BigDecimal value) {
        if (value == null) return "₹ 0.00";
        return "₹ " + value.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }
    
    private void setDefaultCompanySettings(IContext context) {
        context.put("company_name", "");
        context.put("company_address", "");
        context.put("company_city", "");
        context.put("company_state", "");
        context.put("company_pincode", "");
        context.put("company_phone", "");
        context.put("company_email", "");
        context.put("company_gstin", "");
    }
    
    private void generateDefaultTemplate() {
        log.info("Default template generation not implemented yet");
    }
}


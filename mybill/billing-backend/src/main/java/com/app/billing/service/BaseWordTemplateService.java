package com.app.billing.service;

import com.app.billing.model.CompanySettings;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.images.ByteArrayImageProvider;
import fr.opensagres.xdocreport.document.images.IImageProvider;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Base service for PDF generation from Word templates.
 * Provides common functionality for loading templates, processing them, and converting to PDF.
 * 
 * All module-specific PDF services should extend or use this service to ensure consistency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaseWordTemplateService {
    
    protected final CompanySettingsService companySettingsService;
    protected final TemplateResolver templateResolver;
    
    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    protected static final DateTimeFormatter DATE_FORMATTER_LONG = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    
    /**
     * Generate PDF from a Word template.
     * 
     * @param templateType The type of template to use
     * @param reportId Unique identifier for this report generation
     * @param contextPopulator Function to populate the Velocity context with data
     * @return PDF as byte array
     * @throws Exception if generation fails
     */
    protected byte[] generatePdfFromTemplate(
            TemplateResolver.TemplateType templateType,
            String reportId,
            ContextPopulator contextPopulator) throws Exception {
        
        String templatePath = templateResolver.getTemplatePath(templateType);
        log.info("Generating PDF using template: {} for report: {}", templatePath, reportId);
        
        InputStream templateStream = null;
        XWPFDocument xwpfDocument = null;
        InputStream docxInputStream = null;
        
        try {
            // Load template from classpath
            ClassPathResource resource = new ClassPathResource(templatePath);
            if (!resource.exists()) {
                log.error("Template not found at path: {}", templatePath);
                throw new FileNotFoundException("Template not found: " + templatePath);
            }
            
            templateStream = resource.getInputStream();
            
            // Load report with Velocity engine
            IXDocReport report = XDocReportRegistry.getRegistry().loadReport(
                templateStream, 
                reportId, 
                TemplateEngineKind.Velocity
            );
            
            // Create context and populate with data
            IContext context = report.createContext();
            
            // Populate context using the provided function
            contextPopulator.populate(context, report);
            
            // Process template to generate Word document
            ByteArrayOutputStream docxOutputStream = new ByteArrayOutputStream();
            report.process(context, docxOutputStream);
            
            // Convert Word to PDF
            docxInputStream = new ByteArrayInputStream(docxOutputStream.toByteArray());
            xwpfDocument = new XWPFDocument(docxInputStream);
            
            ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
            PdfOptions options = null;
            PdfConverter.getInstance().convert(xwpfDocument, pdfOutputStream, options);
            
            log.info("Successfully generated PDF for report: {}", reportId);
            return pdfOutputStream.toByteArray();
            
        } catch (FileNotFoundException e) {
            log.error("Template file not found: {}", templatePath, e);
            throw new Exception("Template not found: " + templatePath + ". Please ensure the template file exists.", e);
        } catch (Throwable e) {
            log.error("Error generating PDF for report {}: {}", reportId, e.getMessage(), e);
            throw new Exception("Failed to generate PDF: " + e.getMessage(), e);
        } finally {
            // Close resources
            if (xwpfDocument != null) {
                try {
                    xwpfDocument.close();
                } catch (Exception e) {
                    log.warn("Error closing XWPFDocument: {}", e.getMessage());
                }
            }
            if (docxInputStream != null) {
                try {
                    docxInputStream.close();
                } catch (Exception e) {
                    log.warn("Error closing docxInputStream: {}", e.getMessage());
                }
            }
            if (templateStream != null) {
                try {
                    templateStream.close();
                } catch (Exception e) {
                    log.warn("Error closing templateStream: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Populate common company information in the context.
     * This is used by all templates.
     */
    protected void populateCompanyInfo(IContext context, IXDocReport report) throws Exception {
        CompanySettings companySettings = getCompanySettings();
        
        // Company information
        context.put("company_name", getValueOrDefault(companySettings.getCompanyName(), ""));
        context.put("company_address", buildAddress(
            companySettings.getAddress(), 
            companySettings.getCity(), 
            companySettings.getState(), 
            companySettings.getPincode()
        ));
        context.put("company_city", getValueOrDefault(companySettings.getCity(), ""));
        context.put("company_state", getValueOrDefault(companySettings.getState(), ""));
        context.put("company_pincode", getValueOrDefault(companySettings.getPincode(), ""));
        context.put("company_phone", getValueOrDefault(companySettings.getPhone(), ""));
        context.put("company_email", getValueOrDefault(companySettings.getEmail(), ""));
        context.put("company_gstin", getValueOrDefault(companySettings.getGstin(), ""));
        
        // Company logo (if available)
        if (companySettings.getLogoBase64() != null && !companySettings.getLogoBase64().isEmpty()) {
            try {
                String base64Data = companySettings.getLogoBase64();
                if (base64Data.startsWith("data:image")) {
                    base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                }
                byte[] logoBytes = Base64.getDecoder().decode(base64Data);
                IImageProvider logo = new ByteArrayImageProvider(logoBytes);
                
                FieldsMetadata metadata = new FieldsMetadata();
                metadata.addFieldAsImage("company_logo");
                report.setFieldsMetadata(metadata);
                context.put("company_logo", logo);
            } catch (Exception e) {
                log.warn("Failed to load company logo: {}", e.getMessage());
            }
        }
        
        // Footer information
        context.put("footer_slogan", getValueOrDefault(companySettings.getFooterSlogan(), 
                "Thank you for your business!"));
    }
    
    /**
     * Get company settings, with fallback to defaults
     */
    protected CompanySettings getCompanySettings() {
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
                    .logoBase64(settingsDto.getLogoBase64())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to load company settings: {}", e.getMessage());
            return CompanySettings.builder().build();
        }
    }
    
    /**
     * Build full address string
     */
    protected String buildAddress(String address, String city, String state, String pincode) {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isEmpty()) {
            sb.append(address);
        }
        if (city != null && !city.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        if (pincode != null && !pincode.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(pincode);
        }
        return sb.toString();
    }
    
    /**
     * Format date as dd-MM-yyyy
     */
    protected String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }
    
    /**
     * Format date as dd MMMM yyyy
     */
    protected String formatDateLong(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER_LONG) : "";
    }
    
    /**
     * Format decimal number
     */
    protected String formatDecimal(BigDecimal value) {
        return value != null ? value.toPlainString() : "0";
    }
    
    /**
     * Format currency with ₹ symbol
     */
    protected String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "₹ 0.00";
        }
        return "₹ " + String.format("%.2f", value.doubleValue());
    }
    
    /**
     * Get value or default
     */
    protected String getValueOrDefault(String value, String defaultValue) {
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
    
    /**
     * Convert number to words (Indian numbering system)
     */
    protected String convertNumberToWords(BigDecimal amount) {
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
            log.warn("Failed to convert number to words: {}", e.getMessage());
            return "Amount in Words";
        }
    }
    
    /**
     * Convert number to words (0-999999)
     */
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
    
    /**
     * Functional interface for populating Velocity context.
     * Allows each service to define its own context population logic.
     */
    @FunctionalInterface
    public interface ContextPopulator {
        void populate(IContext context, IXDocReport report) throws Exception;
    }
}


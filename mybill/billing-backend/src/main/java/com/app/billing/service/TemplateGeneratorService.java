package com.app.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Service to check for missing Word templates on application startup.
 * 
 * To generate missing templates, run: TemplateGeneratorUtil.main()
 * This will create all required templates in the correct locations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateGeneratorService {
    
    @PostConstruct
    public void checkTemplates() {
        log.info("Checking for missing templates...");
        
        // Check if templates exist and log warnings if missing
        checkTemplate("templates/quotation/QuotationTemplate.docx", "Quotation");
        checkTemplate("templates/reports/SalesReportTemplate.docx", "Sales Report");
        checkTemplate("templates/reports/PurchaseReportTemplate.docx", "Purchase Report");
        checkTemplate("templates/reports/PartyReportTemplate.docx", "Party Report");
        checkTemplate("templates/reports/BillingReportTemplate.docx", "Billing Report");
        checkTemplate("templates/reports/StockReportTemplate.docx", "Stock Report");
        
        log.info("Template check completed. Run TemplateGeneratorUtil.main() to generate missing templates.");
    }
    
    private void checkTemplate(String templatePath, String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            if (!resource.exists()) {
                log.warn("⚠️  Missing template: {} - Run TemplateGeneratorUtil to generate it", templateName);
            } else {
                log.debug("✓ Template exists: {}", templateName);
            }
        } catch (Exception e) {
            log.warn("Could not check template {}: {}", templateName, e.getMessage());
        }
    }
}

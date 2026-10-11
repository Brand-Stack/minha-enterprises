package com.app.billing.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized template resolver for PDF generation.
 * Maps module types to their respective Word template paths.
 * 
 * This allows each module to have its own template with module-specific fields,
 * ensuring PDFs match the data structure and exclude irrelevant fields.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateResolver {
    
    /**
     * Enum defining all template types in the system.
     * Each module has its own template type.
     */
    @Getter
    @RequiredArgsConstructor
    public enum TemplateType {
        /**
         * Billing/Invoice template - for GST and Estimate invoices
         * Note: Keeping old path for backward compatibility, update to templates/billing/ when template is moved
         */
        BILLING("templates/invoice/InvoiceTemplate.docx", "Invoice"),
        
        /**
         * Quotation template - for quotations with valid till, delivery date, etc.
         */
        QUOTATION("templates/quotation/QuotationTemplate.docx", "Quotation"),
        
        /**
         * Purchase Bill template - for purchase bills with supplier details
         */
        PURCHASE_BILL("templates/purchase/PurchaseBillTemplate.docx", "Purchase Bill"),
        
        /**
         * Sales Report template - tabular report format
         */
        SALES_REPORT("templates/reports/SalesReportTemplate.docx", "Sales Report"),
        
        /**
         * Purchase Report template - purchase-specific fields
         */
        PURCHASE_REPORT("templates/reports/PurchaseReportTemplate.docx", "Purchase Report"),
        
        /**
         * Party Report template - party-wise bill listing
         */
        PARTY_REPORT("templates/reports/PartyReportTemplate.docx", "Party Report"),
        
        /**
         * Billing Report template - billing statistics
         */
        BILLING_REPORT("templates/reports/BillingReportTemplate.docx", "Billing Report"),
        
        /**
         * Stock Report template - stock levels and status
         */
        STOCK_REPORT("templates/reports/StockReportTemplate.docx", "Stock Report"),
        
        /**
         * Expense Report template - expense category, amount, payment mode
         */
        EXPENSE_REPORT("templates/reports/ExpenseReportTemplate.docx", "Expense Report"),
        
        /**
         * Cash In Hand Report template - date-wise totals
         */
        CASH_IN_HAND_REPORT("templates/reports/CashInHandReportTemplate.docx", "Cash In Hand Report"),
        
        /**
         * Payment Out template - for payment vouchers
         */
        PAYMENT_OUT("templates/payment/PaymentOutTemplate.docx", "Payment Out");
        
        private final String templatePath;
        private final String displayName;
    }
    
    /**
     * Get template path for a given template type.
     * 
     * @param templateType The template type enum
     * @return The template path relative to classpath resources
     */
    public String getTemplatePath(TemplateType templateType) {
        if (templateType == null) {
            log.warn("Template type is null, defaulting to BILLING template");
            return TemplateType.BILLING.getTemplatePath();
        }
        
        String path = templateType.getTemplatePath();
        log.debug("Resolved template path: {} for type: {}", path, templateType);
        return path;
    }
    
    /**
     * Get template path by string name (for dynamic resolution).
     * Useful for report types that come as strings.
     * 
     * @param templateName The template name (case-insensitive)
     * @return The template path, or BILLING as fallback
     */
    public String getTemplatePathByName(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            log.warn("Template name is empty, defaulting to BILLING template");
            return TemplateType.BILLING.getTemplatePath();
        }
        
        try {
            TemplateType type = TemplateType.valueOf(templateName.toUpperCase());
            return getTemplatePath(type);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown template name: {}, defaulting to BILLING template", templateName);
            return TemplateType.BILLING.getTemplatePath();
        }
    }
    
    /**
     * Get all available template types.
     * Useful for validation and documentation.
     * 
     * @return Map of template type names to their paths
     */
    public Map<String, String> getAllTemplates() {
        Map<String, String> templates = new HashMap<>();
        for (TemplateType type : TemplateType.values()) {
            templates.put(type.name(), type.getTemplatePath());
        }
        return templates;
    }
    
    /**
     * Check if a template type exists.
     * 
     * @param templateType The template type to check
     * @return true if the template type exists
     */
    public boolean templateExists(TemplateType templateType) {
        return templateType != null;
    }
}


package com.app.billing.service;

import com.app.billing.dto.QuotationDto;
import com.app.billing.dto.ClientDto;
import com.app.billing.model.CompanySettings;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for generating quotations from Word templates using XDocReport and Velocity.
 * Uses the same template style as invoices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationWordTemplateService {
    
    private final ClientService clientService;
    private final BaseWordTemplateService baseWordTemplateService;
    
    /**
     * Generate quotation PDF from Word template
     * Uses QUOTATION template type for quotation-specific fields
     */
    public byte[] generateQuotationPdf(QuotationDto quotation) throws Exception {
        log.info("Generating quotation PDF from Word template for quotation: {}", quotation.getQuotationNumber());
        
        String reportId = "Quotation_" + quotation.getQuotationNumber() + "_" + System.currentTimeMillis();
        
        return baseWordTemplateService.generatePdfFromTemplate(
            TemplateResolver.TemplateType.QUOTATION,
            reportId,
            (context, report) -> {
                // Configure FieldsMetadata for items table loop
                FieldsMetadata metadata = new FieldsMetadata();
                metadata.addFieldAsList("items");
                metadata.addFieldAsList("items.item_code");
                metadata.addFieldAsList("items.item_name");
                metadata.addFieldAsList("items.category");
                metadata.addFieldAsList("items.en_code");
                metadata.addFieldAsList("items.quantity");
                metadata.addFieldAsList("items.unit");
                metadata.addFieldAsList("items.mrp");
                metadata.addFieldAsList("items.rate");
                metadata.addFieldAsList("items.tax");
                metadata.addFieldAsList("items.amount");
                report.setFieldsMetadata(metadata);
                
                populateContext(quotation, context, report);
            }
        );
    }
    
    /**
     * Populate Velocity context with quotation data
     */
    private void populateContext(QuotationDto quotation, IContext context, IXDocReport report) throws Exception {
        // Populate common company information
        baseWordTemplateService.populateCompanyInfo(context, report);
        
        // Get company settings for quotation-specific footer
        CompanySettings companySettings = baseWordTemplateService.getCompanySettings();
        
        // Get party details
        ClientDto partyDto = null;
        if (quotation.getPartyId() != null) {
            try {
                partyDto = clientService.findById(quotation.getPartyId());
            } catch (Exception e) {
                log.warn("Failed to load party details for quotation: {}", e.getMessage());
            }
        }
        
        // ========== QUOTATION INFORMATION ==========
        context.put("document_type", "QUOTATION");
        String quotationNumber = baseWordTemplateService.getValueOrDefault(quotation.getQuotationNumber(), "N/A");
        String quotationDate = baseWordTemplateService.formatDate(quotation.getQuotationDate());
        context.put("quotation_number", quotationNumber);
        context.put("quotation_date", quotationDate);
        context.put("invoice_no", quotationNumber);
        context.put("invoice_number", quotationNumber);
        context.put("invoice_date", quotationDate);
        context.put("invoice_date_long", baseWordTemplateService.formatDateLong(quotation.getQuotationDate()));
        
        // Due Date - Use delivery date if available, otherwise use valid till date
        if (quotation.getDeliveryDate() != null) {
            context.put("due_date", baseWordTemplateService.formatDate(quotation.getDeliveryDate()));
        } else if (quotation.getValidTillDays() != null && quotation.getQuotationDate() != null) {
            LocalDate validTillDate = quotation.getQuotationDate().plusDays(quotation.getValidTillDays());
            context.put("due_date", baseWordTemplateService.formatDate(validTillDate));
        } else {
            context.put("due_date", "");
        }
        
        // ========== CUSTOMER/PARTY INFORMATION ==========
        if (partyDto != null) {
            context.put("customer_name", baseWordTemplateService.getValueOrDefault(partyDto.getPartyName(), ""));
            context.put("party_name", baseWordTemplateService.getValueOrDefault(partyDto.getPartyName(), ""));
            context.put("customer_code", baseWordTemplateService.getValueOrDefault(partyDto.getPartyCode(), ""));
            context.put("customer_contact_person", baseWordTemplateService.getValueOrDefault(partyDto.getContactPerson(), ""));
            context.put("customer_phone", baseWordTemplateService.getValueOrDefault(partyDto.getPhone(), ""));
            context.put("party_phone", baseWordTemplateService.getValueOrDefault(partyDto.getPhone(), ""));
            context.put("customer_email", baseWordTemplateService.getValueOrDefault(partyDto.getEmail(), ""));
            context.put("customer_address", baseWordTemplateService.buildAddress(partyDto.getAddress(), partyDto.getCity(), 
                    partyDto.getState(), partyDto.getPincode()));
            context.put("party_address", baseWordTemplateService.buildAddress(partyDto.getAddress(), partyDto.getCity(), 
                    partyDto.getState(), partyDto.getPincode()));
            context.put("customer_city", baseWordTemplateService.getValueOrDefault(partyDto.getCity(), ""));
            context.put("customer_state", baseWordTemplateService.getValueOrDefault(partyDto.getState(), ""));
            context.put("customer_pincode", baseWordTemplateService.getValueOrDefault(partyDto.getPincode(), ""));
            context.put("customer_gstin", baseWordTemplateService.getValueOrDefault(partyDto.getGstin(), ""));
            context.put("party_gstin", baseWordTemplateService.getValueOrDefault(partyDto.getGstin(), ""));
            context.put("place_of_supply", baseWordTemplateService.getValueOrDefault(partyDto.getState(), ""));
        } else {
            // Fallback if party not found
            context.put("customer_name", baseWordTemplateService.getValueOrDefault(quotation.getPartyName(), "N/A"));
            context.put("party_name", baseWordTemplateService.getValueOrDefault(quotation.getPartyName(), "N/A"));
            context.put("customer_code", "");
            context.put("customer_contact_person", "");
            context.put("customer_phone", "");
            context.put("party_phone", "");
            context.put("customer_email", "");
            context.put("customer_address", "");
            context.put("party_address", "");
            context.put("customer_city", "");
            context.put("customer_state", "");
            context.put("customer_pincode", "");
            context.put("customer_gstin", "");
            context.put("party_gstin", "");
            context.put("place_of_supply", "");
        }
        
        // Ship To - Use quotation shipping details if available, otherwise use Bill To
        if (quotation.getShippingToPartyName() != null && !quotation.getShippingToPartyName().isEmpty()) {
            context.put("ship_to_name", quotation.getShippingToPartyName());
            String shippingAddress = baseWordTemplateService.buildAddress(quotation.getShippingAddress(), quotation.getShippingCity(), 
                    quotation.getShippingState(), quotation.getShippingPincode());
            context.put("ship_to_address", shippingAddress);
        } else {
            String customerName = (String) context.get("customer_name");
            String customerAddress = (String) context.get("customer_address");
            context.put("ship_to_name", customerName != null && !customerName.isEmpty() ? customerName : "");
            context.put("ship_to_address", customerAddress != null && !customerAddress.isEmpty() ? customerAddress : "");
        }
        
        // ========== ITEMS LIST ==========
        List<Map<String, Object>> itemsList = new ArrayList<>();
        if (quotation.getItems() != null && !quotation.getItems().isEmpty()) {
            int itemNumber = 1;
            for (QuotationDto.QuotationItemDto item : quotation.getItems()) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("item_number", itemNumber++);
                itemMap.put("item_code", baseWordTemplateService.getValueOrDefault(item.getItemCode(), ""));
                itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getItemName(), "N/A"));
                itemMap.put("category", ""); // Quotation items may not have category
                itemMap.put("en_code", baseWordTemplateService.getValueOrDefault(item.getItemCode(), "")); // Use item code as EN code
                String quantityStr = item.getQuantity() != null ? baseWordTemplateService.formatDecimal(item.getQuantity()) : "0";
                itemMap.put("quantity", quantityStr);
                itemMap.put("unit", ""); // Quotation items may not have unit
                
                // MRP: Show as number without currency, or "-" if not applicable (use unitPrice as MRP for quotations)
                String mrp = item.getUnitPrice() != null ? item.getUnitPrice().stripTrailingZeros().toPlainString() : "-";
                itemMap.put("mrp", mrp);
                
                // Rate and Amount with currency symbol
                itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getUnitPrice()));
                itemMap.put("tax", baseWordTemplateService.formatCurrency(BigDecimal.ZERO)); // Quotation items may not have tax
                itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
                itemsList.add(itemMap);
            }
        }
        context.put("items", itemsList);
        
        // ========== SUMMARY/TOTALS ==========
        context.put("subtotal", baseWordTemplateService.formatCurrency(quotation.getSubtotal()));
        context.put("discount_percent", baseWordTemplateService.formatDecimal(quotation.getDiscountPercent()));
        context.put("discount_amount", baseWordTemplateService.formatCurrency(quotation.getDiscountAmount()));
        context.put("tax_amount", baseWordTemplateService.formatCurrency(quotation.getTaxAmount()));
        context.put("total_amount", baseWordTemplateService.formatCurrency(quotation.getTotalAmount()));
        
        // Payment information - For quotations, these are typically zero
        BigDecimal receivedAmount = BigDecimal.ZERO;
        BigDecimal previousBalance = BigDecimal.ZERO;
        BigDecimal currentBalance = quotation.getTotalAmount().subtract(receivedAmount).add(previousBalance);
        
        context.put("received_amount", baseWordTemplateService.formatCurrency(receivedAmount));
        context.put("previous_balance", baseWordTemplateService.formatCurrency(previousBalance));
        context.put("current_balance", baseWordTemplateService.formatCurrency(currentBalance));
        
        // Amount in words
        context.put("amount_in_words", baseWordTemplateService.convertNumberToWords(quotation.getTotalAmount()));
        
        // ========== DELIVERY DATE ==========
        if (quotation.getDeliveryDate() != null) {
            context.put("delivery_date", baseWordTemplateService.formatDate(quotation.getDeliveryDate()));
            context.put("delivery_date_long", baseWordTemplateService.formatDateLong(quotation.getDeliveryDate()));
        } else {
            context.put("delivery_date", "");
            context.put("delivery_date_long", "");
        }
        
        // ========== VALID TILL ==========
        if (quotation.getValidTillDays() != null && quotation.getQuotationDate() != null) {
            LocalDate validTillDate = quotation.getQuotationDate().plusDays(quotation.getValidTillDays());
            context.put("valid_till", baseWordTemplateService.formatDate(validTillDate));
            context.put("valid_till_long", baseWordTemplateService.formatDateLong(validTillDate));
        } else {
            context.put("valid_till", "");
            context.put("valid_till_long", "");
        }
        
        // ========== FOOTER INFORMATION ==========
        // Note: footerForQuotation may not exist in CompanySettings, using footerSlogan as fallback
        String footerText = companySettings.getFooterSlogan() != null ? companySettings.getFooterSlogan() : "Thank you for your business!";
        context.put("footer_slogan", footerText);
        
        // Notes
        context.put("notes", baseWordTemplateService.getValueOrDefault(quotation.getNotes(), ""));
    }
}


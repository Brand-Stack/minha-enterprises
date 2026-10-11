package com.app.billing.service;

import com.app.billing.dto.InvoiceDto;
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
 * Service for generating invoices from Word templates using XDocReport and Velocity.
 * Follows the same pattern as FXPaymentConfirmGenerator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceWordTemplateService {
    
    private final ClientService clientService;
    private final BaseWordTemplateService baseWordTemplateService;
    
    /**
     * Generate invoice PDF from Word template
     * 
     * @param invoice Invoice DTO containing all invoice data
     * @return PDF as byte array
     * @throws Exception if generation fails
     */
    public byte[] generateInvoicePdf(InvoiceDto invoice) throws Exception {
        log.info("Generating invoice PDF from Word template for invoice: {}", invoice.getInvoiceNumber());
        
        String reportId = "Invoice_" + invoice.getInvoiceNumber() + "_" + System.currentTimeMillis();
        
        return baseWordTemplateService.generatePdfFromTemplate(
            TemplateResolver.TemplateType.BILLING,
            reportId,
            (context, report) -> {
                // Configure FieldsMetadata for items table loop
                FieldsMetadata metadata = new FieldsMetadata();
                metadata.addFieldAsList("items");
                metadata.addFieldAsList("items.item_name");
                metadata.addFieldAsList("items.item_code");
                metadata.addFieldAsList("items.quantity");
                metadata.addFieldAsList("items.mrp");
                metadata.addFieldAsList("items.rate");
                metadata.addFieldAsList("items.amount");
                report.setFieldsMetadata(metadata);
                
                populateContext(invoice, context, report);
            }
        );
    }
    
    /**
     * Populate Velocity context with invoice data
     */
    private void populateContext(InvoiceDto invoice, IContext context, IXDocReport report) throws Exception {
        // Populate common company information
        baseWordTemplateService.populateCompanyInfo(context, report);
        
        // Get party details
        ClientDto partyDto = clientService.findById(invoice.getPartyId());
        
        // ========== INVOICE INFORMATION ==========
        context.put("invoice_no", baseWordTemplateService.getValueOrDefault(invoice.getInvoiceNumber(), "N/A"));
        context.put("invoice_date", baseWordTemplateService.formatDate(invoice.getInvoiceDate()));
        context.put("invoice_date_long", baseWordTemplateService.formatDateLong(invoice.getInvoiceDate()));
        
        // Calculate due date (15 days from invoice date for consistency with sample)
        LocalDate dueDate = invoice.getInvoiceDate() != null 
                ? invoice.getInvoiceDate().plusDays(15) 
                : LocalDate.now().plusDays(15);
        context.put("due_date", baseWordTemplateService.formatDate(dueDate));
        context.put("due_date_long", baseWordTemplateService.formatDateLong(dueDate));
        
        context.put("bill_type", invoice.getBillType() != null ? invoice.getBillType().name() : "GST");
        context.put("payment_status", invoice.getPaymentStatus() != null 
                ? invoice.getPaymentStatus().name() : "PENDING");
        
        // ========== CUSTOMER/PARTY INFORMATION ==========
        if (partyDto != null) {
            context.put("customer_name", baseWordTemplateService.getValueOrDefault(partyDto.getPartyName(), ""));
            context.put("customer_code", baseWordTemplateService.getValueOrDefault(partyDto.getPartyCode(), ""));
            context.put("customer_contact_person", baseWordTemplateService.getValueOrDefault(partyDto.getContactPerson(), ""));
            context.put("customer_phone", baseWordTemplateService.getValueOrDefault(partyDto.getPhone(), ""));
            context.put("customer_email", baseWordTemplateService.getValueOrDefault(partyDto.getEmail(), ""));
            context.put("customer_address", baseWordTemplateService.buildAddress(partyDto.getAddress(), partyDto.getCity(), 
                    partyDto.getState(), partyDto.getPincode()));
            context.put("customer_city", baseWordTemplateService.getValueOrDefault(partyDto.getCity(), ""));
            context.put("customer_state", baseWordTemplateService.getValueOrDefault(partyDto.getState(), ""));
            context.put("customer_pincode", baseWordTemplateService.getValueOrDefault(partyDto.getPincode(), ""));
            context.put("customer_gstin", baseWordTemplateService.getValueOrDefault(partyDto.getGstin(), ""));
            context.put("place_of_supply", baseWordTemplateService.getValueOrDefault(partyDto.getState(), ""));
        } else {
            // Fallback if party not found
            context.put("customer_name", baseWordTemplateService.getValueOrDefault(invoice.getPartyName(), "N/A"));
            context.put("customer_code", "");
            context.put("customer_contact_person", "");
            context.put("customer_phone", "");
            context.put("customer_email", "");
            context.put("customer_address", "");
            context.put("customer_city", "");
            context.put("customer_state", "");
            context.put("customer_pincode", "");
            context.put("customer_gstin", "");
            context.put("place_of_supply", "");
        }
        
        // Ship To (same as Bill To for now, can be customized)
        String customerName = (String) context.get("customer_name");
        String customerAddress = (String) context.get("customer_address");
        context.put("ship_to_name", customerName != null && !customerName.isEmpty() ? customerName : "");
        context.put("ship_to_address", customerAddress != null && !customerAddress.isEmpty() ? customerAddress : "");
        
        // ========== ITEMS LIST ==========
        List<Map<String, Object>> itemsList = new ArrayList<>();
        if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
            log.info("Processing {} items for invoice {}", invoice.getItems().size(), invoice.getInvoiceNumber());
            int itemNumber = 1;
            for (InvoiceDto.InvoiceItemDto item : invoice.getItems()) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("item_number", itemNumber++);
                itemMap.put("item_code", baseWordTemplateService.getValueOrDefault(item.getItemCode(), ""));
                itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getItemName(), "N/A"));
                
                // Format quantity - ensure it's not null
                String quantityStr = item.getQuantity() != null ? baseWordTemplateService.formatDecimal(item.getQuantity()) : "0";
                itemMap.put("quantity", quantityStr);
                
                // MRP: Show as number without currency, or "-" if not applicable
                String mrp = item.getUnitPrice() != null ? item.getUnitPrice().stripTrailingZeros().toPlainString() : "-";
                itemMap.put("mrp", mrp);
                
                // Rate and Amount with currency symbol
                itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getUnitPrice()));
                itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
                itemsList.add(itemMap);
                log.debug("Added item: {} - Qty: {}, Rate: {}, Amount: {}", 
                    itemMap.get("item_name"), quantityStr, itemMap.get("rate"), itemMap.get("amount"));
            }
        } else {
            log.warn("No items found in invoice {} - items list is null or empty", invoice.getInvoiceNumber());
        }
        context.put("items", itemsList);
        log.info("Total items added to context: {} for invoice {}", itemsList.size(), invoice.getInvoiceNumber());
        
        // ========== SUMMARY/TOTALS ==========
        context.put("subtotal", baseWordTemplateService.formatCurrency(invoice.getSubtotal()));
        context.put("discount_percent", baseWordTemplateService.formatDecimal(invoice.getDiscountPercent()));
        context.put("discount_amount", baseWordTemplateService.formatCurrency(invoice.getDiscountAmount()));
        
        // GST details
        if (invoice.getBillType() != null && invoice.getBillType() == com.app.billing.model.Invoice.BillType.GST) {
            context.put("cgst_amount", baseWordTemplateService.formatCurrency(invoice.getCgst()));
            context.put("sgst_amount", baseWordTemplateService.formatCurrency(invoice.getSgst()));
            context.put("cgst_percent", baseWordTemplateService.formatDecimal(invoice.getCgst() != null && invoice.getSubtotal() != null 
                    && invoice.getSubtotal().compareTo(BigDecimal.ZERO) > 0
                    ? invoice.getCgst().divide(invoice.getSubtotal(), 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO));
            context.put("sgst_percent", baseWordTemplateService.formatDecimal(invoice.getSgst() != null && invoice.getSubtotal() != null 
                    && invoice.getSubtotal().compareTo(BigDecimal.ZERO) > 0
                    ? invoice.getSgst().divide(invoice.getSubtotal(), 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO));
        } else {
            context.put("cgst_amount", baseWordTemplateService.formatCurrency(BigDecimal.ZERO));
            context.put("sgst_amount", baseWordTemplateService.formatCurrency(BigDecimal.ZERO));
            context.put("cgst_percent", "0.00");
            context.put("sgst_percent", "0.00");
        }
        
        context.put("total_amount", baseWordTemplateService.formatCurrency(invoice.getTotalAmount()));
        
        // Payment information
        BigDecimal receivedAmount = BigDecimal.ZERO; // Can be calculated from payment history
        BigDecimal previousBalance = BigDecimal.ZERO; // Can be fetched from account balance
        BigDecimal currentBalance = invoice.getTotalAmount().subtract(receivedAmount).add(previousBalance);
        
        context.put("received_amount", baseWordTemplateService.formatCurrency(receivedAmount));
        context.put("previous_balance", baseWordTemplateService.formatCurrency(previousBalance));
        context.put("current_balance", baseWordTemplateService.formatCurrency(currentBalance));
        
        // Amount in words
        context.put("amount_in_words", baseWordTemplateService.convertNumberToWords(invoice.getTotalAmount()));
        
        // ========== FOOTER INFORMATION ==========
        CompanySettings companySettings = baseWordTemplateService.getCompanySettings();
        context.put("bank_details", getBankDetails(companySettings)); // Can be customized
        context.put("terms_conditions", getTermsAndConditions(companySettings)); // Can be customized
        
        // Notes
        context.put("notes", baseWordTemplateService.getValueOrDefault(invoice.getNotes(), ""));
    }
    
    /**
     * Get bank details (can be customized per company)
     */
    private String getBankDetails(CompanySettings companySettings) {
        // Return empty string for now - can be extended to store in company settings
        // Format: "Bank Name: ABC Bank\nAccount No: 1234567890\nIFSC: ABC0001234\nBranch: Branch Name"
        return "";
    }
    
    /**
     * Get terms and conditions (can be customized per company)
     */
    private String getTermsAndConditions(CompanySettings companySettings) {
        // Return default terms - can be extended to store in company settings
        String defaultTerms = "Payment due within 15 days. Goods once sold will not be taken back.";
        return defaultTerms;
    }
}


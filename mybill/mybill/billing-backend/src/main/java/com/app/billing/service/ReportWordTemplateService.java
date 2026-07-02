package com.app.billing.service;

import com.app.billing.dao.ItemRepository;
import com.app.billing.dto.DashboardDto;
import com.app.billing.model.Item;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.Optional;

/**
 * Service for generating report PDFs from Word templates using XDocReport and Velocity.
 * Uses the same template structure as InvoiceWordTemplateService for consistency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportWordTemplateService {
    
    private final BaseWordTemplateService baseWordTemplateService;
    private final ItemRepository itemRepository;
    
    /**
     * Generate Sales Report PDF from Word template
     * Uses SALES_REPORT template type
     */
    public byte[] generateSalesReportPdf(List<DashboardDto.SalesData> data, LocalDate startDate, LocalDate endDate) throws Exception {
        log.info("Generating Sales Report PDF from Word template");
        return generateReportPdf(TemplateResolver.TemplateType.SALES_REPORT, "SALES REPORT", data, startDate, endDate, "sales");
    }
    
    /**
     * Generate Purchase Report PDF from Word template
     * Uses PURCHASE_REPORT template type
     */
    public byte[] generatePurchaseReportPdf(List<DashboardDto.PurchaseData> data, LocalDate startDate, LocalDate endDate) throws Exception {
        log.info("Generating Purchase Report PDF from Word template");
        return generateReportPdf(TemplateResolver.TemplateType.PURCHASE_REPORT, "PURCHASE REPORT", data, startDate, endDate, "purchase");
    }
    
    /**
     * Generate Party Report PDF from Word template
     * Uses PARTY_REPORT template type
     */
    public byte[] generatePartyReportPdf(List<DashboardDto.PartyData> data, LocalDate startDate, LocalDate endDate) throws Exception {
        log.info("Generating Party Report PDF from Word template");
        return generateReportPdf(TemplateResolver.TemplateType.PARTY_REPORT, "PARTY-WISE REPORT", data, startDate, endDate, "party");
    }
    
    /**
     * Generate Billing Report PDF from Word template
     * Uses BILLING_REPORT template type
     */
    public byte[] generateBillingReportPdf(List<DashboardDto.BillingData> data, LocalDate startDate, LocalDate endDate, String billType) throws Exception {
        log.info("Generating Billing Report PDF from Word template");
        String title = "BILLING REPORT";
        if (billType != null && !billType.isEmpty()) {
            title += " (" + billType + " Bills)";
        }
        return generateReportPdf(TemplateResolver.TemplateType.BILLING_REPORT, title, data, startDate, endDate, "billing");
    }
    
    /**
     * Generate Stock Report PDF from Word template
     * Uses STOCK_REPORT template type
     */
    public byte[] generateStockReportPdf(List<DashboardDto.StockData> data) throws Exception {
        log.info("Generating Stock Report PDF from Word template");
        return generateReportPdf(TemplateResolver.TemplateType.STOCK_REPORT, "STOCK REPORT", data, LocalDate.now(), LocalDate.now(), "stock");
    }
    
    /**
     * Generate Expense Report PDF from Word template
     * Uses EXPENSE_REPORT template type
     */
    public byte[] generateExpenseReportPdf(List<DashboardDto.ExpenseData> data, LocalDate startDate, LocalDate endDate) throws Exception {
        log.info("Generating Expense Report PDF from Word template");
        return generateReportPdf(TemplateResolver.TemplateType.EXPENSE_REPORT, "EXPENSE REPORT", data, startDate, endDate, "expense");
    }
    
    /**
     * Generic method to generate report PDF using Word template
     * Each report type uses its own specific template
     */
    private byte[] generateReportPdf(TemplateResolver.TemplateType templateType, String reportTitle, 
                                     List<?> data, LocalDate startDate, LocalDate endDate, String reportType) throws Exception {
        String reportId = "Report_" + reportType + "_" + System.currentTimeMillis();
        
        return baseWordTemplateService.generatePdfFromTemplate(
            templateType,
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
                
                // Populate context based on report type
                populateReportContext(context, report, reportTitle, data, startDate, endDate, reportType);
            }
        );
    }
    
    /**
     * Populate Velocity context with report data
     */
    private void populateReportContext(IContext context, IXDocReport report, String reportTitle, 
                                      List<?> data, LocalDate startDate, LocalDate endDate, String reportType) throws Exception {
        // Populate common company information
        baseWordTemplateService.populateCompanyInfo(context, report);
        
        // ========== REPORT INFORMATION ==========
        context.put("document_type", reportTitle);
        context.put("invoice_no", reportTitle); // Use report title in invoice number field
        context.put("invoice_number", reportTitle);
        context.put("invoice_date", baseWordTemplateService.formatDate(startDate));
        context.put("invoice_date_long", baseWordTemplateService.formatDateLong(startDate));
        context.put("due_date", baseWordTemplateService.formatDate(endDate));
        context.put("due_date_long", baseWordTemplateService.formatDateLong(endDate));
        context.put("start_date", baseWordTemplateService.formatDate(startDate));
        context.put("end_date", baseWordTemplateService.formatDate(endDate));
        context.put("date", baseWordTemplateService.formatDate(startDate));
        context.put("generated_date", baseWordTemplateService.formatDateLong(LocalDate.now()));
        context.put("page_number", "1"); // Page number placeholder
        
        // ========== REPORT-SPECIFIC DATA ==========
        List<Map<String, Object>> itemsList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        @SuppressWarnings("unchecked")
        List<?> typedData = data;
        switch (reportType) {
            case "sales":
                itemsList = populateSalesItems((List<DashboardDto.SalesData>) typedData);
                totalAmount = ((List<DashboardDto.SalesData>) typedData).stream()
                    .map(DashboardDto.SalesData::getTotalRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                break;
            case "purchase":
                itemsList = populatePurchaseItems((List<DashboardDto.PurchaseData>) typedData);
                totalAmount = ((List<DashboardDto.PurchaseData>) typedData).stream()
                    .map(DashboardDto.PurchaseData::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                break;
            case "party":
                itemsList = populatePartyItems((List<DashboardDto.PartyData>) typedData);
                totalAmount = ((List<DashboardDto.PartyData>) typedData).stream()
                    .map(DashboardDto.PartyData::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                break;
            case "billing":
                itemsList = populateBillingItems((List<DashboardDto.BillingData>) typedData);
                totalAmount = ((List<DashboardDto.BillingData>) typedData).stream()
                    .map(DashboardDto.BillingData::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                break;
            case "stock":
                itemsList = populateStockItems((List<DashboardDto.StockData>) typedData);
                totalAmount = ((List<DashboardDto.StockData>) typedData).stream()
                    .map(DashboardDto.StockData::getStockValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                break;
            case "expense":
                itemsList = populateExpenseItems((List<DashboardDto.ExpenseData>) typedData);
                totalAmount = ((List<DashboardDto.ExpenseData>) typedData).stream()
                    .map(DashboardDto.ExpenseData::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                break;
        }
        
        context.put("items", itemsList);
        
        // ========== SUMMARY/TOTALS ==========
        context.put("subtotal", baseWordTemplateService.formatCurrency(totalAmount));
        context.put("discount_percent", "0.00");
        context.put("discount_amount", baseWordTemplateService.formatCurrency(BigDecimal.ZERO));
        context.put("tax_amount", baseWordTemplateService.formatCurrency(BigDecimal.ZERO));
        context.put("total_amount", baseWordTemplateService.formatCurrency(totalAmount));
        
        // Payment information - Not applicable for reports
        context.put("received_amount", baseWordTemplateService.formatCurrency(BigDecimal.ZERO));
        context.put("previous_balance", baseWordTemplateService.formatCurrency(BigDecimal.ZERO));
        context.put("current_balance", baseWordTemplateService.formatCurrency(totalAmount));
        context.put("payment_mode", ""); // Payment mode placeholder
        context.put("created_by", ""); // Created by placeholder
        
        // Amount in words
        context.put("amount_in_words", baseWordTemplateService.convertNumberToWords(totalAmount));
        
        // ========== CUSTOMER/PARTY INFORMATION ==========
        // For reports, we don't have a specific party, so leave empty or use report info
        context.put("customer_name", "");
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
        context.put("party_name", "");
        context.put("party_phone", "");
        context.put("party_address", "");
        context.put("party_gstin", "");
        
        // Ship To - Not applicable for reports
        context.put("ship_to_name", "");
        context.put("ship_to_address", "");
        
        // ========== FOOTER INFORMATION ==========
        context.put("notes", "Report generated on " + baseWordTemplateService.formatDateLong(LocalDate.now()));
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populateSalesItems(List<DashboardDto.SalesData> data) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        int itemNumber = 1;
        for (DashboardDto.SalesData item : data) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item_number", itemNumber++);
            itemMap.put("invoice_no", ""); // Sales data may not have invoice number
            itemMap.put("date", ""); // Sales data may not have date
            itemMap.put("party_name", ""); // Sales data may not have party name
            itemMap.put("item_code", baseWordTemplateService.getValueOrDefault(item.getItemCode(), ""));
            itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getItemName(), "N/A"));
            itemMap.put("category", ""); // Sales data may not have category
            itemMap.put("quantity", baseWordTemplateService.formatDecimal(item.getQuantitySold()));
            itemMap.put("mrp", "-");
            BigDecimal qty = item.getQuantitySold() != null && item.getQuantitySold().compareTo(BigDecimal.ZERO) > 0 
                ? item.getQuantitySold() : BigDecimal.ONE;
            itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getTotalRevenue().divide(qty, 2, java.math.RoundingMode.HALF_UP)));
            itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getTotalRevenue()));
            itemsList.add(itemMap);
        }
        return itemsList;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populatePurchaseItems(List<DashboardDto.PurchaseData> data) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        int itemNumber = 1;
        for (DashboardDto.PurchaseData item : data) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item_number", itemNumber++);
            itemMap.put("item_code", baseWordTemplateService.getValueOrDefault(item.getItemCode(), ""));
            itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getItemName(), "N/A"));
            itemMap.put("category", ""); // Purchase data may not have category
            itemMap.put("quantity", baseWordTemplateService.formatDecimal(item.getQuantity()));
            itemMap.put("unit", ""); // Purchase data may not have unit
            itemMap.put("mrp", item.getUnitPrice() != null ? item.getUnitPrice().stripTrailingZeros().toPlainString() : "-");
            itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getUnitPrice()));
            itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
            itemsList.add(itemMap);
        }
        return itemsList;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populatePartyItems(List<DashboardDto.PartyData> data) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        int itemNumber = 1;
        for (DashboardDto.PartyData item : data) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item_number", itemNumber++);
            itemMap.put("invoice_no", ""); // Party data may not have invoice number
            itemMap.put("date", ""); // Party data may not have date
            itemMap.put("bill_amount", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
            itemMap.put("payment_status", ""); // Party data may not have payment status
            itemMap.put("item_code", "");
            itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getPartyName(), "N/A"));
            itemMap.put("quantity", String.valueOf(item.getTotalBills()));
            itemMap.put("mrp", "-");
            long bills = item.getTotalBills() != null && item.getTotalBills() > 0 ? item.getTotalBills() : 1;
            itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getTotalAmount().divide(new BigDecimal(bills), 2, java.math.RoundingMode.HALF_UP)));
            itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
            itemsList.add(itemMap);
        }
        return itemsList;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populateBillingItems(List<DashboardDto.BillingData> data) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        int itemNumber = 1;
        for (DashboardDto.BillingData item : data) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item_number", itemNumber++);
            // Billing Report shows period summaries: Date, Invoice Count, Total Amount
            itemMap.put("item_code", "");
            itemMap.put("item_name", baseWordTemplateService.formatDate(item.getDate() != null ? item.getDate() : LocalDate.now()));
            itemMap.put("category", String.valueOf(item.getTotalInvoices())); // Invoice count in category column
            itemMap.put("en_code", ""); // Not applicable
            itemMap.put("quantity", ""); // Not applicable
            itemMap.put("unit", ""); // Not applicable
            itemMap.put("mrp", "-");
            itemMap.put("rate", ""); // Not applicable
            itemMap.put("tax", ""); // Not applicable
            itemMap.put("total", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
            itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getTotalAmount()));
            itemsList.add(itemMap);
        }
        return itemsList;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populateStockItems(List<DashboardDto.StockData> data) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        int itemNumber = 1;
        for (DashboardDto.StockData item : data) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item_number", itemNumber++);
            itemMap.put("item_code", baseWordTemplateService.getValueOrDefault(item.getItemCode(), ""));
            itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getItemName(), "N/A"));
            
            // Fetch category and unit from Item entity
            String category = "";
            String unit = "";
            try {
                Optional<Item> itemEntity = itemRepository.findByItemCode(item.getItemCode());
                if (itemEntity.isPresent()) {
                    category = baseWordTemplateService.getValueOrDefault(itemEntity.get().getCategory(), "");
                    unit = baseWordTemplateService.getValueOrDefault(itemEntity.get().getUnit(), "");
                }
            } catch (Exception e) {
                log.warn("Could not fetch item details for stock report: {}", e.getMessage());
            }
            
            itemMap.put("category", category);
            itemMap.put("unit", unit);
            itemMap.put("stock_quantity", baseWordTemplateService.formatDecimal(item.getCurrentStock()));
            
            // Use status from StockData, format it properly
            String status = baseWordTemplateService.getValueOrDefault(item.getStatus(), "OK");
            if ("NEGATIVE".equalsIgnoreCase(status)) {
                status = "Negative";
            } else if ("OUT_OF_STOCK".equalsIgnoreCase(status)) {
                status = "Out of Stock";
            } else {
                status = "OK";
            }
            itemMap.put("status", status);
            itemMap.put("quantity", baseWordTemplateService.formatDecimal(item.getCurrentStock()));
            itemMap.put("mrp", "-");
            BigDecimal stock = item.getCurrentStock() != null && item.getCurrentStock().compareTo(BigDecimal.ZERO) > 0 
                ? item.getCurrentStock() : BigDecimal.ONE;
            itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getStockValue().divide(stock, 2, java.math.RoundingMode.HALF_UP)));
            itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getStockValue()));
            itemsList.add(itemMap);
        }
        return itemsList;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> populateExpenseItems(List<DashboardDto.ExpenseData> data) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        int itemNumber = 1;
        for (DashboardDto.ExpenseData item : data) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item_number", itemNumber++);
            itemMap.put("expense_number", baseWordTemplateService.getValueOrDefault(item.getExpenseNumber(), ""));
            itemMap.put("date", baseWordTemplateService.formatDate(item.getExpenseDate()));
            itemMap.put("category", baseWordTemplateService.getValueOrDefault(item.getCategory(), ""));
            itemMap.put("party_name", baseWordTemplateService.getValueOrDefault(item.getPartyName(), ""));
            itemMap.put("payment_mode", baseWordTemplateService.getValueOrDefault(item.getPaymentMode(), ""));
            itemMap.put("description", baseWordTemplateService.getValueOrDefault(item.getDescription(), ""));
            itemMap.put("expense_details", baseWordTemplateService.getValueOrDefault(item.getExpenseDetails(), ""));
            itemMap.put("is_auto_generated", item.getIsAutoGenerated() != null && item.getIsAutoGenerated() ? "Yes" : "No");
            itemMap.put("item_code", ""); // Not applicable for expenses
            itemMap.put("item_name", baseWordTemplateService.getValueOrDefault(item.getCategory(), "N/A"));
            itemMap.put("quantity", "1");
            itemMap.put("mrp", "-");
            itemMap.put("rate", baseWordTemplateService.formatCurrency(item.getAmount()));
            itemMap.put("amount", baseWordTemplateService.formatCurrency(item.getAmount()));
            itemsList.add(itemMap);
        }
        return itemsList;
    }
}


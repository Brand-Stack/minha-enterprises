package com.app.billing.service;

import com.app.billing.dao.ExpenseRepository;
import com.app.billing.dao.InvoiceRepository;
import com.app.billing.dao.ItemRepository;
import com.app.billing.dao.ClientRepository;
import com.app.billing.dao.PurchaseBillRepository;
import com.app.billing.dto.DashboardDto;
import com.app.billing.model.Expense;
import com.app.billing.model.Invoice;
import com.app.billing.model.Item;
import com.app.billing.model.Client;
import com.app.billing.model.PurchaseBill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final InvoiceRepository invoiceRepository;
    private final ItemRepository itemRepository;
    private final ClientRepository clientRepository;
    private final PurchaseBillRepository purchaseBillRepository;
    private final ExpenseRepository expenseRepository;
    
    public List<DashboardDto.TurnoverData> getTurnoverData(LocalDate startDate, LocalDate endDate, String groupBy) {
        // Filter only GST bills for dashboard
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate)
                .stream()
                .filter(inv -> inv.getBillType() == Invoice.BillType.GST)
                .toList();
        
        Map<String, DashboardDto.TurnoverData> turnoverMap = new HashMap<>();
        
        for (Invoice invoice : invoices) {
            String period = getPeriodKey(invoice.getInvoiceDate(), groupBy);
            
            turnoverMap.computeIfAbsent(period, k -> DashboardDto.TurnoverData.builder()
                    .period(period)
                    .date(invoice.getInvoiceDate())
                    .amount(BigDecimal.ZERO)
                    .invoiceCount(0L)
                    .build());
            
            DashboardDto.TurnoverData data = turnoverMap.get(period);
            data.setAmount(data.getAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            data.setInvoiceCount(data.getInvoiceCount() + 1);
        }
        
        return new ArrayList<>(turnoverMap.values());
    }
    
    public List<DashboardDto.BillingData> getBillingData(LocalDate startDate, LocalDate endDate, String groupBy, String billType) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
        
        // Filter by bill type if specified
        if (billType != null && !billType.isEmpty()) {
            Invoice.BillType type = "GST".equalsIgnoreCase(billType) ? Invoice.BillType.GST : Invoice.BillType.ESTIMATE;
            invoices = invoices.stream()
                    .filter(inv -> inv.getBillType() == type)
                    .toList();
        }
        
        Map<String, DashboardDto.BillingData> billingMap = new HashMap<>();
        
        for (Invoice invoice : invoices) {
            String period = getPeriodKey(invoice.getInvoiceDate(), groupBy);
            
            billingMap.computeIfAbsent(period, k -> DashboardDto.BillingData.builder()
                    .period(period)
                    .date(invoice.getInvoiceDate())
                    .totalInvoices(0L)
                    .estimateCount(0L)
                    .gstBillCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .estimateAmount(BigDecimal.ZERO)
                    .gstAmount(BigDecimal.ZERO)
                    .build());
            
            DashboardDto.BillingData data = billingMap.get(period);
            data.setTotalInvoices(data.getTotalInvoices() + 1);
            data.setTotalAmount(data.getTotalAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            
            // Count by bill type
            if (invoice.getBillType() == Invoice.BillType.GST) {
                data.setGstBillCount(data.getGstBillCount() + 1);
                data.setGstAmount(data.getGstAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            } else if (invoice.getBillType() == Invoice.BillType.ESTIMATE) {
                data.setEstimateCount(data.getEstimateCount() + 1);
                data.setEstimateAmount(data.getEstimateAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            }
        }
        
        return new ArrayList<>(billingMap.values());
    }
    
    public List<DashboardDto.StockData> getStockData() {
        List<Item> items = itemRepository.findAll();
        
        return items.stream()
                .map(item -> {
                    BigDecimal stock = item.getStockQuantity() != null ? item.getStockQuantity() : BigDecimal.ZERO;
                    BigDecimal minStock = item.getMinStockLevel() != null ? item.getMinStockLevel() : BigDecimal.ZERO;
                    BigDecimal sellingPrice = item.getSellingPrice() != null ? item.getSellingPrice() : BigDecimal.ZERO;
                    
                    String status;
                    if (stock.compareTo(BigDecimal.ZERO) == 0) {
                        status = "OUT_OF_STOCK";
                    } else if (minStock.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(minStock) < 0) {
                        status = "LOW";
                    } else {
                        status = "OK";
                    }
                    
                    return DashboardDto.StockData.builder()
                            .itemCode(item.getItemCode())
                            .itemName(item.getItemName())
                            .currentStock(stock)
                            .minStockLevel(minStock)
                            .status(status)
                            .stockValue(stock.multiply(sellingPrice))
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    public List<DashboardDto.SalesData> getSalesData(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
        
        Map<String, DashboardDto.SalesData> salesMap = new HashMap<>();
        
        for (Invoice invoice : invoices) {
            // Only count GST bills for sales (Estimate bills don't affect stock/sales)
            if (invoice.getBillType() == Invoice.BillType.GST) {
                for (Invoice.InvoiceItem item : invoice.getItems()) {
                    String key = item.getItemCode();
                    
                    salesMap.computeIfAbsent(key, k -> DashboardDto.SalesData.builder()
                            .itemCode(item.getItemCode())
                            .itemName(item.getItemName())
                            .quantitySold(BigDecimal.ZERO)
                            .totalRevenue(BigDecimal.ZERO)
                            .invoiceCount(0L)
                            .build());
                    
                    DashboardDto.SalesData data = salesMap.get(key);
                    data.setQuantitySold(data.getQuantitySold().add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO));
                    data.setTotalRevenue(data.getTotalRevenue().add(item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO));
                    data.setInvoiceCount(data.getInvoiceCount() + 1);
                }
            }
        }
        
        // Sort by quantity sold (descending) - Top selling items
        return salesMap.values().stream()
                .sorted((a, b) -> b.getQuantitySold().compareTo(a.getQuantitySold()))
                .collect(Collectors.toList());
    }
    
    public DashboardDto.DashboardSummary getDashboardSummary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = YearMonth.now().atDay(1);
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        LocalDate dayStart = today;
        
        // Filter only GST bills for dashboard
        List<Invoice> allInvoices = invoiceRepository.findAll()
                .stream()
                .filter(inv -> inv.getBillType() == Invoice.BillType.GST)
                .toList();
        List<Invoice> monthlyInvoices = invoiceRepository.findByInvoiceDateBetween(monthStart, today)
                .stream()
                .filter(inv -> inv.getBillType() == Invoice.BillType.GST)
                .toList();
        List<Invoice> dailyInvoices = invoiceRepository.findByInvoiceDateBetween(dayStart, today)
                .stream()
                .filter(inv -> inv.getBillType() == Invoice.BillType.GST)
                .toList();
        List<Invoice> yearlyInvoices = invoiceRepository.findByInvoiceDateBetween(yearStart, today)
                .stream()
                .filter(inv -> inv.getBillType() == Invoice.BillType.GST)
                .toList();
        List<Item> items = itemRepository.findAll();
        
        BigDecimal totalRevenue = allInvoices.stream()
                .map(inv -> inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal monthlyRevenue = monthlyInvoices.stream()
                .map(inv -> inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal dailyRevenue = dailyInvoices.stream()
                .map(inv -> inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal yearlyRevenue = yearlyInvoices.stream()
                .map(inv -> inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long lowStockItems = items.stream()
                .filter(item -> {
                    BigDecimal stock = item.getStockQuantity() != null ? item.getStockQuantity() : BigDecimal.ZERO;
                    BigDecimal minStock = item.getMinStockLevel() != null ? item.getMinStockLevel() : BigDecimal.ZERO;
                    return minStock.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(minStock) < 0;
                })
                .count();
        
        long outOfStockItems = items.stream()
                .filter(item -> {
                    BigDecimal stock = item.getStockQuantity() != null ? item.getStockQuantity() : BigDecimal.ZERO;
                    return stock.compareTo(BigDecimal.ZERO) == 0;
                })
                .count();
        
        BigDecimal totalStockValue = items.stream()
                .map(item -> {
                    BigDecimal stock = item.getStockQuantity() != null ? item.getStockQuantity() : BigDecimal.ZERO;
                    BigDecimal price = item.getSellingPrice() != null ? item.getSellingPrice() : BigDecimal.ZERO;
                    return stock.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long totalCustomers = clientRepository.findAll().size();
        
        return DashboardDto.DashboardSummary.builder()
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .dailyRevenue(dailyRevenue)
                .yearlyRevenue(yearlyRevenue)
                .totalInvoices((long) allInvoices.size())
                .monthlyInvoices((long) monthlyInvoices.size())
                .lowStockItems((int) lowStockItems)
                .outOfStockItems((int) outOfStockItems)
                .totalCustomers(totalCustomers)
                .totalStockValue(totalStockValue)
                .build();
    }
    
    public List<DashboardDto.PurchaseData> getPurchaseData(LocalDate startDate, LocalDate endDate, String itemFilter, String categoryFilter) {
        // Get ALL purchases from PurchaseBill (both PAID and UNPAID) for reporting
        // Reports should show all historical data, not just unpaid
        // Use findAll and filter manually to ensure we get all records (MongoDB Between might have timezone issues)
        List<PurchaseBill> allBills = purchaseBillRepository.findAll();
        List<PurchaseBill> purchaseBills = new ArrayList<>();
        
        // Filter by date range manually (inclusive on both ends)
        for (PurchaseBill bill : allBills) {
            if (bill.getBillDate() != null) {
                // Check if date is within range (inclusive): startDate <= billDate <= endDate
                if (!bill.getBillDate().isBefore(startDate) && !bill.getBillDate().isAfter(endDate)) {
                    purchaseBills.add(bill);
                }
            }
        }
        
        List<DashboardDto.PurchaseData> purchaseData = new ArrayList<>();
        
        for (PurchaseBill bill : purchaseBills) {
            // Verify party is Supplier (should already be enforced, but double-check)
            // If partyId is null, skip the check and include the bill
            if (bill.getPartyId() != null && !bill.getPartyId().trim().isEmpty()) {
                Client client = clientRepository.findById(bill.getPartyId()).orElse(null);
                if (client != null && client.getPartyType() != Client.ClientType.SUPPLIER) {
                    continue; // Skip non-supplier purchases
                }
            }
            
            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                for (PurchaseBill.PurchaseItem item : bill.getItems()) {
                    // Get itemCode and itemName - fetch from Item master if missing
                    String itemCode = item.getItemCode();
                    String itemName = item.getItemName();
                    
                    // Always try to fetch from Item master if itemId exists (even if itemCode/itemName exist)
                    // This ensures we have the most up-to-date information
                    if (item.getItemId() != null && !item.getItemId().trim().isEmpty()) {
                        Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                        if (itemEntity != null) {
                            // Use Item master values if available, otherwise use what's in PurchaseItem
                            itemCode = (itemEntity.getItemCode() != null && !itemEntity.getItemCode().trim().isEmpty()) 
                                    ? itemEntity.getItemCode() 
                                    : (itemCode != null && !itemCode.trim().isEmpty() ? itemCode : "");
                            itemName = (itemEntity.getItemName() != null && !itemEntity.getItemName().trim().isEmpty()) 
                                    ? itemEntity.getItemName() 
                                    : (itemName != null && !itemName.trim().isEmpty() ? itemName : "");
                        }
                    }
                    
                    // Skip only if both are still missing after trying to fetch
                    // But be lenient - if we have itemId, try to include it with placeholder names
                    if ((itemCode == null || itemCode.trim().isEmpty()) &&
                        (itemName == null || itemName.trim().isEmpty())) {
                        // If we have itemId but no name/code, use itemId as fallback
                        if (item.getItemId() != null && !item.getItemId().trim().isEmpty()) {
                            itemCode = item.getItemId();
                            itemName = "Item " + item.getItemId();
                        } else {
                            continue; // Skip if we have no way to identify the item
                        }
                    }
                    
                    // Filter by item if specified
                    if (itemFilter != null && !itemFilter.isEmpty()) {
                        String filterLower = itemFilter.toLowerCase();
                        boolean matches = false;
                        if (itemName != null && itemName.toLowerCase().contains(filterLower)) {
                            matches = true;
                        }
                        if (!matches && itemCode != null && itemCode.toLowerCase().contains(filterLower)) {
                            matches = true;
                        }
                        if (!matches) {
                            continue;
                        }
                    }
                    
                    // Filter by category if specified
                    if (categoryFilter != null && !categoryFilter.isEmpty()) {
                        // Try to get category from item first, then from Item master
                        String itemCategory = item.getCategory();
                        if (itemCategory == null || itemCategory.trim().isEmpty()) {
                            Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                            if (itemEntity != null) {
                                itemCategory = itemEntity.getCategory();
                            }
                        }
                        if (itemCategory == null || !categoryFilter.equalsIgnoreCase(itemCategory)) {
                            continue;
                        }
                    }
                    
                    purchaseData.add(DashboardDto.PurchaseData.builder()
                            .billNumber(bill.getBillNumber() != null ? bill.getBillNumber() : "—")
                            .billDate(bill.getBillDate())
                            .partyName(bill.getPartyName() != null ? bill.getPartyName() : "Unknown")
                            .itemCode(itemCode != null ? itemCode : "")
                            .itemName(itemName != null ? itemName : "")
                            .quantity(item.getQuantity() != null ? BigDecimal.valueOf(item.getQuantity()) : BigDecimal.ZERO)
                            .unitPrice(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO)
                            .totalAmount(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO)
                            .paymentType(bill.getPaymentType() != null ? bill.getPaymentType().toString() : "")
                            .paymentStatus(bill.getPaymentStatus() != null ? bill.getPaymentStatus().toString() : "")
                            .onlinePaymentMethod(bill.getOnlinePaymentMethod() != null ? bill.getOnlinePaymentMethod().toString() : "")
                            .paidAmount(bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO)
                            .outstandingAmount(bill.getOutstandingAmount() != null ? bill.getOutstandingAmount() : BigDecimal.ZERO)
                            .build());
                }
            }
        }
        
        return purchaseData;
    }
    
    /**
     * Get Expense Report Data
     */
    public List<DashboardDto.ExpenseData> getExpenseData(LocalDate startDate, LocalDate endDate, String categoryFilter) {
        // Get all expenses and filter by date range manually to ensure we catch all records
        List<Expense> allExpenses = expenseRepository.findAll();
        List<Expense> expenses = new ArrayList<>();
        
        // Filter by date range manually (inclusive on both ends)
        for (Expense expense : allExpenses) {
            if (expense.getExpenseDate() != null) {
                // Check if date is within range (inclusive): startDate <= expenseDate <= endDate
                if (!expense.getExpenseDate().isBefore(startDate) && !expense.getExpenseDate().isAfter(endDate)) {
                    expenses.add(expense);
                }
            }
        }
        
        List<DashboardDto.ExpenseData> expenseData = new ArrayList<>();
        
        for (Expense expense : expenses) {
            // Filter by category if specified
            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                if (expense.getCategory() == null || !categoryFilter.equalsIgnoreCase(expense.getCategory())) {
                    continue;
                }
            }
            
            expenseData.add(DashboardDto.ExpenseData.builder()
                    .expenseNumber(expense.getExpenseNumber() != null ? expense.getExpenseNumber() : "")
                    .expenseDate(expense.getExpenseDate())
                    .category(expense.getCategory() != null ? expense.getCategory() : "")
                    .partyName(expense.getPartyName() != null ? expense.getPartyName() : "")
                    .paymentMode(expense.getPaymentType() != null ? expense.getPaymentType().toString() : "")
                    .amount(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO)
                    .description(expense.getDescription() != null ? expense.getDescription() : "")
                    .expenseDetails(expense.getExpenseDetails() != null ? expense.getExpenseDetails() : "")
                    .isAutoGenerated(expense.getPurchaseBillId() != null && !expense.getPurchaseBillId().trim().isEmpty())
                    .transactionType(expense.getTransactionType() != null ? expense.getTransactionType().name() : "CASH_IN")
                    .build());
        }
        
        return expenseData;
    }
    
    public DashboardDto.CashInHandData getCashInHandData(LocalDate date) {
        // If date is null, use yesterday
        if (date == null) {
            date = LocalDate.now().minusDays(1);
        }
        
        // Money IN: use receivedCashAmount/receivedOnlineAmount only (migration fallback when null)
        List<Invoice> dayInvoices = invoiceRepository.findByInvoiceDateBetween(date, date)
                .stream()
                .filter(inv -> inv.getBillType() == Invoice.BillType.GST)
                .filter(inv -> inv.getStatus() != Invoice.BillStatus.DRAFT && (inv.getStatus() == null || inv.getStatus() != Invoice.BillStatus.RETURNED))
                .toList();
        BigDecimal cashReceived = BigDecimal.ZERO;
        BigDecimal onlineReceived = BigDecimal.ZERO;
        for (Invoice inv : dayInvoices) {
            BigDecimal cash = inv.getReceivedCashAmount() != null ? inv.getReceivedCashAmount() : BigDecimal.ZERO;
            BigDecimal online = inv.getReceivedOnlineAmount() != null ? inv.getReceivedOnlineAmount() : BigDecimal.ZERO;
            if (cash.compareTo(BigDecimal.ZERO) == 0 && online.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
                if (inv.getPaymentStatus() == Invoice.PaymentStatus.PENDING) paid = BigDecimal.ZERO;
                else if (inv.getPaymentStatus() == Invoice.PaymentStatus.PAID) paid = inv.getTotalAmount() != null ? inv.getTotalAmount() : paid;
                if (inv.getModeOfPayment() == Invoice.PaymentMode.CASH) { cash = paid; online = BigDecimal.ZERO; }
                else { cash = BigDecimal.ZERO; online = paid; }
            }
            cashReceived = cashReceived.add(cash);
            onlineReceived = onlineReceived.add(online);
        }
        BigDecimal totalSales = cashReceived.add(onlineReceived);
        
        // Money OUT: paidCashAmount + paidOnlineAmount (migration fallback)
        List<Expense> dayExpenses = expenseRepository.findByExpenseDateBetween(date, date);
        BigDecimal expenseCash = BigDecimal.ZERO, expenseOnline = BigDecimal.ZERO;
        for (Expense exp : dayExpenses) {
            BigDecimal cash = exp.getPaidCashAmount() != null ? exp.getPaidCashAmount() : BigDecimal.ZERO;
            BigDecimal online = exp.getPaidOnlineAmount() != null ? exp.getPaidOnlineAmount() : BigDecimal.ZERO;
            if (cash.compareTo(BigDecimal.ZERO) == 0 && online.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal paid = exp.getAmount() != null ? exp.getAmount() : BigDecimal.ZERO;
                if (exp.getPaymentType() == Expense.PaymentType.CASH) { cash = paid; online = BigDecimal.ZERO; }
                else { cash = BigDecimal.ZERO; online = paid; }
            }
            expenseCash = expenseCash.add(cash);
            expenseOnline = expenseOnline.add(online);
        }
        List<PurchaseBill> dayPurchases = purchaseBillRepository.findByBillDateBetween(date, date);
        BigDecimal purchaseCash = BigDecimal.ZERO, purchaseOnline = BigDecimal.ZERO;
        for (PurchaseBill p : dayPurchases) {
            BigDecimal cash = p.getPaidCashAmount() != null ? p.getPaidCashAmount() : BigDecimal.ZERO;
            BigDecimal online = p.getPaidOnlineAmount() != null ? p.getPaidOnlineAmount() : BigDecimal.ZERO;
            if (cash.compareTo(BigDecimal.ZERO) == 0 && online.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal paid = p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO;
                if (p.getPaymentStatus() == PurchaseBill.PaymentStatus.PAID) paid = p.getTotalAmount() != null ? p.getTotalAmount() : paid;
                else if (p.getPaymentStatus() == PurchaseBill.PaymentStatus.UNPAID) paid = BigDecimal.ZERO;
                if (p.getPaymentType() == PurchaseBill.PaymentType.CASH) { cash = paid; online = BigDecimal.ZERO; }
                else { cash = BigDecimal.ZERO; online = paid; }
            }
            purchaseCash = purchaseCash.add(cash);
            purchaseOnline = purchaseOnline.add(online);
        }
        BigDecimal totalCashExpenses = expenseCash.add(purchaseCash);
        BigDecimal totalOnlineExpenses = expenseOnline.add(purchaseOnline);
        BigDecimal totalExpenses = totalCashExpenses.add(totalOnlineExpenses);
        BigDecimal liquidCash = cashReceived.subtract(totalCashExpenses);
        BigDecimal onlineBalance = onlineReceived.subtract(totalOnlineExpenses);
        BigDecimal totalAvailableBalance = liquidCash.add(onlineBalance);
        
        return DashboardDto.CashInHandData.builder()
                .date(date)
                .totalSales(totalSales)
                .totalExpenses(totalExpenses)
                .liquidCash(liquidCash)
                .onlineBalance(onlineBalance)
                .totalAvailableBalance(totalAvailableBalance)
                .build();
    }
    
    public List<DashboardDto.SalesData> getSalesDataWithFilters(LocalDate startDate, LocalDate endDate, String categoryFilter, String itemFilter, String modeOfPaymentFilter, String onlinePaymentMethodFilter) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
        
        Map<String, DashboardDto.SalesData> salesMap = new HashMap<>();
        
        for (Invoice invoice : invoices) {
            // Only count GST bills for sales (exclude DRAFT bills)
            // Treat null status as NORMAL (for backward compatibility with older invoices)
            Invoice.BillStatus status = invoice.getStatus() != null ? invoice.getStatus() : Invoice.BillStatus.NORMAL;
            if (invoice.getBillType() == Invoice.BillType.GST && status != Invoice.BillStatus.DRAFT) {
                
                // Filter by mode of payment if specified
                if (modeOfPaymentFilter != null && !modeOfPaymentFilter.isEmpty()) {
                    if (invoice.getModeOfPayment() == null || 
                        !invoice.getModeOfPayment().toString().equalsIgnoreCase(modeOfPaymentFilter)) {
                        continue;
                    }
                }
                
                // Filter by online payment method if specified
                if (onlinePaymentMethodFilter != null && !onlinePaymentMethodFilter.isEmpty()) {
                    if (invoice.getOnlinePaymentMethod() == null || 
                        !invoice.getOnlinePaymentMethod().toString().equalsIgnoreCase(onlinePaymentMethodFilter)) {
                        continue;
                    }
                }
                
                for (Invoice.InvoiceItem item : invoice.getItems()) {
                    // Filter by item if specified
                    if (itemFilter != null && !itemFilter.isEmpty()) {
                        String filterLower = itemFilter.toLowerCase();
                        if (!item.getItemName().toLowerCase().contains(filterLower) && 
                            !item.getItemCode().toLowerCase().contains(filterLower)) {
                            continue;
                        }
                    }
                    
                    // Filter by category if specified
                    if (categoryFilter != null && !categoryFilter.isEmpty()) {
                        Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                        if (itemEntity == null || !categoryFilter.equalsIgnoreCase(itemEntity.getCategory())) {
                            continue;
                        }
                    }
                    
                    String key = item.getItemCode();
                    
                    salesMap.computeIfAbsent(key, k -> DashboardDto.SalesData.builder()
                            .itemCode(item.getItemCode())
                            .itemName(item.getItemName())
                            .quantitySold(BigDecimal.ZERO)
                            .totalRevenue(BigDecimal.ZERO)
                            .invoiceCount(0L)
                            .modeOfPayment("")
                            .onlinePaymentMethod("")
                            .paidAmount(BigDecimal.ZERO)
                            .pendingAmount(BigDecimal.ZERO)
                            .build());
                    
                    DashboardDto.SalesData data = salesMap.get(key);
                    data.setQuantitySold(data.getQuantitySold().add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO));
                    data.setTotalRevenue(data.getTotalRevenue().add(item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO));
                    data.setInvoiceCount(data.getInvoiceCount() + 1);
                    
                    // Set payment fields from invoice
                    if (invoice.getModeOfPayment() != null) {
                        data.setModeOfPayment(invoice.getModeOfPayment().toString());
                    }
                    if (invoice.getOnlinePaymentMethod() != null) {
                        data.setOnlinePaymentMethod(invoice.getOnlinePaymentMethod().toString());
                    }
                    // Aggregate paid and pending amounts
                    BigDecimal invoicePaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
                    BigDecimal invoicePending = invoice.getBalanceAmount() != null ? invoice.getBalanceAmount() : BigDecimal.ZERO;
                    data.setPaidAmount(data.getPaidAmount().add(invoicePaid));
                    data.setPendingAmount(data.getPendingAmount().add(invoicePending));
                }
            }
        }
        
        // Sort by quantity sold (descending)
        return salesMap.values().stream()
                .sorted((a, b) -> b.getQuantitySold().compareTo(a.getQuantitySold()))
                .collect(Collectors.toList());
    }
    
    public List<DashboardDto.BillingData> getBillingDataWithFilters(LocalDate startDate, LocalDate endDate, String billType, String partyId, String invoiceNumber) {
        log.debug("Transaction Report Query: startDate={}, endDate={}, billType={}, partyId={}, invoiceNumber={}", 
                  startDate, endDate, billType, partyId, invoiceNumber);
        
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
        log.info("Transaction Report: found {} invoices in date range {} to {} (inclusive)", invoices.size(), startDate, endDate);
        
        invoices.stream().limit(5).forEach(inv -> 
            log.debug("Invoice: {} | Date: {} | Type: {} | Status: {} | PaymentStatus: {} | Total: {}", 
                      inv.getInvoiceNumber(), inv.getInvoiceDate(), inv.getBillType(), 
                      inv.getStatus(), inv.getPaymentStatus(), inv.getTotalAmount()));
        
        // IMPORTANT: Exclude DRAFT invoices from reports (they are not finalized)
        // Include: NORMAL, CORRECTED, RETURNED. Treat null status as NORMAL (backward compatibility)
        int beforeDraftFilter = invoices.size();
        invoices = invoices.stream()
                .filter(inv -> inv.getStatus() == null || inv.getStatus() != Invoice.BillStatus.DRAFT)
                .toList();
        log.debug("After excluding DRAFT: {} invoices (removed {})", invoices.size(), beforeDraftFilter - invoices.size());
        
        // Filter by bill type if specified
        if (billType != null && !billType.isEmpty()) {
            Invoice.BillType type = "GST".equalsIgnoreCase(billType) ? Invoice.BillType.GST : Invoice.BillType.ESTIMATE;
            invoices = invoices.stream()
                    .filter(inv -> inv.getBillType() == type)
                    .toList();
        }
        
        // Filter by party if specified (null-safe: only filter when partyId is provided)
        if (partyId != null && !partyId.isEmpty()) {
            invoices = invoices.stream()
                    .filter(inv -> inv.getPartyId() != null && inv.getPartyId().equals(partyId))
                    .toList();
            log.debug("After party filter: {} invoices", invoices.size());
        }
        
        // Filter by invoice number if specified
        if (invoiceNumber != null && !invoiceNumber.isEmpty()) {
            String invoiceNumLower = invoiceNumber.toLowerCase();
            invoices = invoices.stream()
                    .filter(inv -> inv.getInvoiceNumber() != null && 
                            inv.getInvoiceNumber().toLowerCase().contains(invoiceNumLower))
                    .toList();
        }
        
        Map<String, DashboardDto.BillingData> billingMap = new HashMap<>();
        
        for (Invoice invoice : invoices) {
            String period = invoice.getInvoiceDate().toString();
            
            billingMap.computeIfAbsent(period, k -> DashboardDto.BillingData.builder()
                    .period(period)
                    .date(invoice.getInvoiceDate())
                    .totalInvoices(0L)
                    .estimateCount(0L)
                    .gstBillCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .estimateAmount(BigDecimal.ZERO)
                    .gstAmount(BigDecimal.ZERO)
                    .build());
            
            DashboardDto.BillingData data = billingMap.get(period);
            data.setTotalInvoices(data.getTotalInvoices() + 1);
            data.setTotalAmount(data.getTotalAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            
            if (invoice.getBillType() == Invoice.BillType.GST) {
                data.setGstBillCount(data.getGstBillCount() + 1);
                data.setGstAmount(data.getGstAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            } else if (invoice.getBillType() == Invoice.BillType.ESTIMATE) {
                data.setEstimateCount(data.getEstimateCount() + 1);
                data.setEstimateAmount(data.getEstimateAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            }
        }
        
        List<DashboardDto.BillingData> result = new ArrayList<>(billingMap.values());
        log.info("Transaction Report: returning {} date-periods for range {} to {} ({} invoices)", 
                 result.size(), startDate, endDate, invoices.size());
        return result;
    }
    
    /**
     * Get invoice-level data for Transaction Report Excel export.
     * Same filters as getBillingDataWithFilters, but returns individual invoice rows.
     */
    public List<DashboardDto.TransactionReportRow> getTransactionReportDetailRows(LocalDate startDate, LocalDate endDate, 
            String billType, String partyId, String invoiceNumber) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
        
        // Exclude DRAFT (same logic as aggregated report)
        invoices = invoices.stream()
                .filter(inv -> inv.getStatus() == null || inv.getStatus() != Invoice.BillStatus.DRAFT)
                .toList();
        
        if (billType != null && !billType.isEmpty()) {
            Invoice.BillType type = "GST".equalsIgnoreCase(billType) ? Invoice.BillType.GST : Invoice.BillType.ESTIMATE;
            invoices = invoices.stream().filter(inv -> inv.getBillType() == type).toList();
        }
        if (partyId != null && !partyId.isEmpty()) {
            invoices = invoices.stream()
                    .filter(inv -> inv.getPartyId() != null && inv.getPartyId().equals(partyId))
                    .toList();
        }
        if (invoiceNumber != null && !invoiceNumber.isEmpty()) {
            String numLower = invoiceNumber.toLowerCase();
            invoices = invoices.stream()
                    .filter(inv -> inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(numLower))
                    .toList();
        }
        
        List<DashboardDto.TransactionReportRow> rows = new ArrayList<>();
        for (Invoice inv : invoices) {
            BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal received = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
            if (received.compareTo(BigDecimal.ZERO) == 0 && inv.getReceivedCashAmount() != null && inv.getReceivedOnlineAmount() != null) {
                received = inv.getReceivedCashAmount().add(inv.getReceivedOnlineAmount());
            }
            BigDecimal pending = total.subtract(received);
            
            rows.add(DashboardDto.TransactionReportRow.builder()
                    .date(inv.getInvoiceDate())
                    .billNumber(inv.getInvoiceNumber())
                    .partyName(inv.getPartyName())
                    .billType(inv.getBillType() != null ? inv.getBillType().name() : "")
                    .totalAmount(total)
                    .receivedAmount(received)
                    .pendingAmount(pending)
                    .paymentStatus(inv.getPaymentStatus() != null ? inv.getPaymentStatus().name() : "")
                    .paymentMode(inv.getModeOfPayment() != null ? inv.getModeOfPayment().name() : "")
                    .status(inv.getStatus() != null ? inv.getStatus().name() : "")
                    .build());
        }
        
        log.info("Transaction Report Detail: {} invoice rows for Excel export", rows.size());
        return rows;
    }
    
    public List<DashboardDto.StockData> getStockDataWithFilters(String categoryFilter, String searchTerm) {
        List<Item> items = itemRepository.findAll();
        
        return items.stream()
                .filter(item -> {
                    // Filter by category if specified
                    if (categoryFilter != null && !categoryFilter.isEmpty()) {
                        if (!categoryFilter.equalsIgnoreCase(item.getCategory())) {
                            return false;
                        }
                    }
                    
                    // Filter by search term if specified
                    if (searchTerm != null && !searchTerm.isEmpty()) {
                        String searchLower = searchTerm.toLowerCase();
                        boolean matches = (item.getItemName() != null && item.getItemName().toLowerCase().contains(searchLower)) ||
                                (item.getItemCode() != null && item.getItemCode().toLowerCase().contains(searchLower)) ||
                                (item.getEnCode() != null && item.getEnCode().toLowerCase().contains(searchLower));
                        if (!matches) {
                            return false;
                        }
                    }
                    
                    return true;
                })
                .map(item -> {
                    BigDecimal stock = item.getStockQuantity() != null ? item.getStockQuantity() : BigDecimal.ZERO;
                    BigDecimal minStock = item.getMinStockLevel() != null ? item.getMinStockLevel() : BigDecimal.ZERO;
                    BigDecimal sellingPrice = item.getSellingPrice() != null ? item.getSellingPrice() : BigDecimal.ZERO;
                    
                    String status;
                    if (stock.compareTo(BigDecimal.ZERO) > 0) {
                        status = "OK"; // Positive stock - OK (Green)
                    } else if (stock.compareTo(BigDecimal.ZERO) < 0) {
                        status = "NEGATIVE"; // Negative stock - Negative (Red)
                    } else {
                        status = "OUT_OF_STOCK"; // Zero stock
                    }
                    
                    return DashboardDto.StockData.builder()
                            .itemCode(item.getItemCode())
                            .itemName(item.getItemName())
                            .currentStock(stock)
                            .minStockLevel(minStock)
                            .status(status)
                            .stockValue(stock.multiply(sellingPrice))
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    public List<DashboardDto.PartyData> getPartyData(LocalDate startDate, LocalDate endDate, String billType) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
        
        // Filter by bill type if specified
        if (billType != null && !billType.isEmpty()) {
            Invoice.BillType type = "GST".equalsIgnoreCase(billType) ? Invoice.BillType.GST : Invoice.BillType.ESTIMATE;
            invoices = invoices.stream()
                    .filter(inv -> inv.getBillType() == type)
                    .toList();
        }
        
        Map<String, DashboardDto.PartyData> partyMap = new HashMap<>();
        
        for (Invoice invoice : invoices) {
            String partyId = invoice.getPartyId();
            String partyName = invoice.getPartyName() != null ? invoice.getPartyName() : "Unknown";
            
            partyMap.computeIfAbsent(partyId, k -> DashboardDto.PartyData.builder()
                    .partyId(partyId)
                    .partyName(partyName)
                    .totalBills(0L)
                    .gstBills(0L)
                    .estimateBills(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .gstAmount(BigDecimal.ZERO)
                    .estimateAmount(BigDecimal.ZERO)
                    .build());
            
            DashboardDto.PartyData data = partyMap.get(partyId);
            data.setTotalBills(data.getTotalBills() + 1);
            data.setTotalAmount(data.getTotalAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            
            if (invoice.getBillType() == Invoice.BillType.GST) {
                data.setGstBills(data.getGstBills() + 1);
                data.setGstAmount(data.getGstAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            } else if (invoice.getBillType() == Invoice.BillType.ESTIMATE) {
                data.setEstimateBills(data.getEstimateBills() + 1);
                data.setEstimateAmount(data.getEstimateAmount().add(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO));
            }
        }
        
        return new ArrayList<>(partyMap.values());
    }
    
    private String getPeriodKey(LocalDate date, String groupBy) {
        switch (groupBy.toUpperCase()) {
            case "WEEK":
                return date.getYear() + "-W" + date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            case "MONTH":
                return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "YEAR":
                return String.valueOf(date.getYear());
            default: // DAY
                return date.toString();
        }
    }
}


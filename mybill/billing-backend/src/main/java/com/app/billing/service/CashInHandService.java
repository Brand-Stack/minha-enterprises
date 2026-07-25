package com.app.billing.service;

import com.app.billing.dao.ExpenseRepository;
import com.app.billing.dao.InvoiceRepository;
import com.app.billing.dao.PurchaseBillRepository;
import com.app.billing.dto.CashInHandDto;
import com.app.billing.model.Expense;
import com.app.billing.model.Invoice;
import com.app.billing.model.PurchaseBill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashInHandService {
    
    private final InvoiceRepository invoiceRepository;
    private final PurchaseBillRepository purchaseBillRepository;
    private final ExpenseRepository expenseRepository;
    
    /**
     * Get Cash In Hand summary with optional date filtering.
     * Starting balance = closing balance at end of day before period start (e.g. yesterday's end for "Today").
     * Closing balance = starting balance + (period Money IN - period Money OUT).
     */
    public CashInHandDto getCashInHandSummary(LocalDate startDate, LocalDate endDate, String filterType) {
        LocalDate[] dateRange = calculateDateRange(startDate, endDate, filterType);
        LocalDate effectiveStartDate = dateRange[0];
        LocalDate effectiveEndDate = dateRange[1];
        
        // Starting balance = closing balance at end of (startDate - 1 day)
        LocalDate dayBeforeStart = effectiveStartDate.minusDays(1);
        BigDecimal[] starting = getClosingBalanceAsOf(dayBeforeStart);
        BigDecimal startingLiquidCash = starting[0];
        BigDecimal startingOnlineBalance = starting[1];
        BigDecimal startingTotalBalance = starting[2];
        
        // Period entries and totals
        List<CashInHandDto.SalesEntry> salesEntries = getSalesEntries(effectiveStartDate, effectiveEndDate);
        List<CashInHandDto.ExpenseEntry> expenseEntries = getExpenseEntries(effectiveStartDate, effectiveEndDate);
        
        BigDecimal totalCashReceived = salesEntries.stream()
                .map(CashInHandDto.SalesEntry::getReceivedCashAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOnlineReceived = salesEntries.stream()
                .map(CashInHandDto.SalesEntry::getReceivedOnlineAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSalesReceived = totalCashReceived.add(totalOnlineReceived);
        
        BigDecimal totalCashExpenses = expenseEntries.stream()
                .map(CashInHandDto.ExpenseEntry::getPaidCashAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOnlineExpenses = expenseEntries.stream()
                .map(CashInHandDto.ExpenseEntry::getPaidOnlineAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpensesPaid = totalCashExpenses.add(totalOnlineExpenses);
        
        // Closing balance = starting + period movement
        BigDecimal liquidCash = startingLiquidCash.add(totalCashReceived).subtract(totalCashExpenses);
        BigDecimal onlineBalance = startingOnlineBalance.add(totalOnlineReceived).subtract(totalOnlineExpenses);
        BigDecimal totalAvailableBalance = liquidCash.add(onlineBalance);
        
        return CashInHandDto.builder()
                .startingLiquidCash(startingLiquidCash)
                .startingOnlineBalance(startingOnlineBalance)
                .startingTotalBalance(startingTotalBalance)
                .liquidCash(liquidCash)
                .onlineBalance(onlineBalance)
                .totalAvailableBalance(totalAvailableBalance)
                .totalCashReceived(totalCashReceived)
                .totalOnlineReceived(totalOnlineReceived)
                .totalSalesReceived(totalSalesReceived)
                .totalCashExpenses(totalCashExpenses)
                .totalOnlineExpenses(totalOnlineExpenses)
                .totalExpensesPaid(totalExpensesPaid)
                .salesEntries(salesEntries)
                .expenseEntries(expenseEntries)
                .startDate(effectiveStartDate)
                .endDate(effectiveEndDate)
                .filterType(filterType != null ? filterType : "CUSTOM")
                .build();
    }
    
    /**
     * Closing balance as of end of given date (all transactions up to and including asOfDate).
     * Returns [liquidCash, onlineBalance, total].
     */
    private BigDecimal[] getClosingBalanceAsOf(LocalDate asOfDate) {
        List<CashInHandDto.SalesEntry> allSales = getSalesEntries(LocalDate.of(1900, 1, 1), asOfDate);
        List<CashInHandDto.ExpenseEntry> allExpenses = getExpenseEntries(LocalDate.of(1900, 1, 1), asOfDate);
        BigDecimal cashIn = allSales.stream()
                .map(CashInHandDto.SalesEntry::getReceivedCashAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal onlineIn = allSales.stream()
                .map(CashInHandDto.SalesEntry::getReceivedOnlineAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashOut = allExpenses.stream()
                .map(CashInHandDto.ExpenseEntry::getPaidCashAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal onlineOut = allExpenses.stream()
                .map(CashInHandDto.ExpenseEntry::getPaidOnlineAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liquid = cashIn.subtract(cashOut);
        BigDecimal online = onlineIn.subtract(onlineOut);
        return new BigDecimal[]{liquid, online, liquid.add(online)};
    }
    
    /**
     * Get only sales summary (for dashboard card click)
     */
    public List<CashInHandDto.SalesEntry> getSalesDrillDown(LocalDate startDate, LocalDate endDate, String filterType) {
        LocalDate[] dateRange = calculateDateRange(startDate, endDate, filterType);
        return getSalesEntries(dateRange[0], dateRange[1]);
    }
    
    /**
     * Get only expense summary (for dashboard card click)
     */
    public List<CashInHandDto.ExpenseEntry> getExpensesDrillDown(LocalDate startDate, LocalDate endDate, String filterType) {
        LocalDate[] dateRange = calculateDateRange(startDate, endDate, filterType);
        return getExpenseEntries(dateRange[0], dateRange[1]);
    }
    
    private List<CashInHandDto.SalesEntry> getSalesEntries(LocalDate startDate, LocalDate endDate) {
        List<CashInHandDto.SalesEntry> salesEntries = new ArrayList<>();
        
        // Money IN: from Invoices/Transactions ONLY. Exclude Draft, Returned (and Quotations are never included).
        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getInvoiceDate() != null)
                .filter(inv -> !inv.getInvoiceDate().isBefore(startDate) && !inv.getInvoiceDate().isAfter(endDate))
                .filter(inv -> inv.getStatus() != Invoice.BillStatus.DRAFT && (inv.getStatus() == null || inv.getStatus() != Invoice.BillStatus.RETURNED))
                .collect(Collectors.toList());
        
        for (Invoice invoice : invoices) {
            BigDecimal totalAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal receivedCashAmount = invoice.getReceivedCashAmount() != null ? invoice.getReceivedCashAmount() : BigDecimal.ZERO;
            BigDecimal receivedOnlineAmount = invoice.getReceivedOnlineAmount() != null ? invoice.getReceivedOnlineAmount() : BigDecimal.ZERO;
            
            // DEBUG: Log invoice payment details
            log.debug("Processing Invoice: {} | Type: {} | Status: {} | PaymentStatus: {} | Mode: {} | " +
                      "Total: {} | Paid: {} | CashStored: {} | OnlineStored: {}",
                      invoice.getInvoiceNumber(), invoice.getBillType(), invoice.getStatus(),
                      invoice.getPaymentStatus(), invoice.getModeOfPayment(),
                      totalAmount, invoice.getPaidAmount(), receivedCashAmount, receivedOnlineAmount);
            
            // Migration: if receivedCashAmount + receivedOnlineAmount are both 0 but we have paidAmount/mode, derive
            if (receivedCashAmount.compareTo(BigDecimal.ZERO) == 0 && receivedOnlineAmount.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
                if (invoice.getPaymentStatus() == Invoice.PaymentStatus.PENDING || paid.compareTo(BigDecimal.ZERO) == 0) {
                    receivedCashAmount = BigDecimal.ZERO;
                    receivedOnlineAmount = BigDecimal.ZERO;
                } else if (invoice.getModeOfPayment() == Invoice.PaymentMode.CASH) {
                    receivedCashAmount = paid;
                    receivedOnlineAmount = BigDecimal.ZERO;
                } else if (invoice.getModeOfPayment() == Invoice.PaymentMode.ONLINE || invoice.getModeOfPayment() == Invoice.PaymentMode.CHEQUE) {
                    receivedCashAmount = BigDecimal.ZERO;
                    receivedOnlineAmount = paid;
                } else {
                    // PARTIAL mode but no split stored → treat as Online per migration rule
                    receivedCashAmount = BigDecimal.ZERO;
                    receivedOnlineAmount = paid;
                }
            }
            
            BigDecimal receivedAmount = receivedCashAmount.add(receivedOnlineAmount);
            BigDecimal balanceAmount = totalAmount.subtract(receivedAmount);
            String moduleType = invoice.getBillType() == Invoice.BillType.GST ? "GST_INVOICE" : "ESTIMATE";
            
            // DEBUG: Log derived values after migration logic
            log.debug("Derived for {}: CashDerived: {} | OnlineDerived: {} | TotalReceived: {}",
                      invoice.getInvoiceNumber(), receivedCashAmount, receivedOnlineAmount, receivedAmount);
            
            salesEntries.add(CashInHandDto.SalesEntry.builder()
                    .id(invoice.getId())
                    .date(invoice.getInvoiceDate())
                    .moduleType(moduleType)
                    .partyName(invoice.getPartyName())
                    .billNumber(invoice.getInvoiceNumber())
                    .itemsTotal(totalAmount)
                    .receivedAmount(receivedAmount)
                    .receivedCashAmount(receivedCashAmount)
                    .receivedOnlineAmount(receivedOnlineAmount)
                    .pendingAmount(balanceAmount)
                    .paymentStatus(invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : "PENDING")
                    .paymentMode(invoice.getModeOfPayment() != null ? invoice.getModeOfPayment().name() : "CASH")
                    .build());
        }
        
        // Quotations are NOT included in Cash In Hand (non-ledger estimates only).
        
        // Sort by date descending - handle null dates safely
        salesEntries.sort(Comparator.comparing(
            CashInHandDto.SalesEntry::getDate, 
            Comparator.nullsLast(Comparator.reverseOrder())
        ));
        
        return salesEntries;
    }
    
    private List<CashInHandDto.ExpenseEntry> getExpenseEntries(LocalDate startDate, LocalDate endDate) {
        List<CashInHandDto.ExpenseEntry> expenseEntries = new ArrayList<>();
        
        // Get Purchase Bills
        List<PurchaseBill> purchases = purchaseBillRepository.findAll().stream()
                .filter(p -> p.getBillDate() != null)
                .filter(p -> !p.getBillDate().isBefore(startDate) && !p.getBillDate().isAfter(endDate))
                .collect(Collectors.toList());
        
        for (PurchaseBill purchase : purchases) {
            BigDecimal totalAmount = purchase.getTotalAmount() != null ? purchase.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal paidCashAmount = purchase.getPaidCashAmount() != null ? purchase.getPaidCashAmount() : BigDecimal.ZERO;
            BigDecimal paidOnlineAmount = purchase.getPaidOnlineAmount() != null ? purchase.getPaidOnlineAmount() : BigDecimal.ZERO;
            BigDecimal paidAmount = paidCashAmount.add(paidOnlineAmount);
            
            // Migration: if both 0 but we have paidAmount/paymentStatus, derive from paymentType
            if (paidCashAmount.compareTo(BigDecimal.ZERO) == 0 && paidOnlineAmount.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal legacyPaid = purchase.getPaidAmount() != null ? purchase.getPaidAmount() : BigDecimal.ZERO;
                if (purchase.getPaymentStatus() == PurchaseBill.PaymentStatus.PAID) {
                    legacyPaid = totalAmount;
                } else if (purchase.getPaymentStatus() == PurchaseBill.PaymentStatus.UNPAID) {
                    legacyPaid = BigDecimal.ZERO;
                }
                if (purchase.getPaymentType() == PurchaseBill.PaymentType.CASH) {
                    paidCashAmount = legacyPaid;
                    paidOnlineAmount = BigDecimal.ZERO;
                } else {
                    paidCashAmount = BigDecimal.ZERO;
                    paidOnlineAmount = legacyPaid;
                }
                paidAmount = paidCashAmount.add(paidOnlineAmount);
            }
            BigDecimal outstandingAmount = totalAmount.subtract(paidAmount);
            
            expenseEntries.add(CashInHandDto.ExpenseEntry.builder()
                    .id(purchase.getId())
                    .date(purchase.getBillDate())
                    .moduleType("PURCHASE")
                    .partyName(purchase.getPartyName())
                    .billNumber(purchase.getBillNumber())
                    .itemsTotal(totalAmount)
                    .paidAmount(paidAmount)
                    .paidCashAmount(paidCashAmount)
                    .paidOnlineAmount(paidOnlineAmount)
                    .pendingAmount(outstandingAmount)
                    .paymentStatus(purchase.getPaymentStatus() != null ? purchase.getPaymentStatus().name() : "UNPAID")
                    .paymentType(purchase.getPaymentType() != null ? purchase.getPaymentType().name() : "CASH")
                    .category(null)
                    .transactionType("CASH_OUT")
                    .build());
        }
        
        // Get Expenses
        List<Expense> expenses = expenseRepository.findAll().stream()
                .filter(e -> e.getExpenseDate() != null)
                .filter(e -> !e.getExpenseDate().isBefore(startDate) && !e.getExpenseDate().isAfter(endDate))
                .collect(Collectors.toList());
        
        for (Expense expense : expenses) {
            BigDecimal amount = expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
            BigDecimal paidCashAmount = expense.getPaidCashAmount() != null ? expense.getPaidCashAmount() : BigDecimal.ZERO;
            BigDecimal paidOnlineAmount = expense.getPaidOnlineAmount() != null ? expense.getPaidOnlineAmount() : BigDecimal.ZERO;
            BigDecimal paidAmount = paidCashAmount.add(paidOnlineAmount);
            BigDecimal pendingAmount = amount.subtract(paidAmount);
            
            // Migration: if both 0, derive from paymentType (since amount is fully paid)
            if (paidCashAmount.compareTo(BigDecimal.ZERO) == 0 && paidOnlineAmount.compareTo(BigDecimal.ZERO) == 0) {
                if (expense.getPaymentType() == Expense.PaymentType.CASH) {
                    paidCashAmount = amount;
                    paidOnlineAmount = BigDecimal.ZERO;
                } else {
                    paidCashAmount = BigDecimal.ZERO;
                    paidOnlineAmount = amount;
                }
                paidAmount = amount;
                pendingAmount = BigDecimal.ZERO;
            }
            String paymentStatus = "PAID";
            
            // Negate cash inflows so subtraction is addition
            if (expense.getTransactionType() == Expense.TransactionType.CASH_IN) {
                amount = amount.negate();
                paidCashAmount = paidCashAmount.negate();
                paidOnlineAmount = paidOnlineAmount.negate();
                paidAmount = paidAmount.negate();
                pendingAmount = pendingAmount.negate();
            }
            
            expenseEntries.add(CashInHandDto.ExpenseEntry.builder()
                    .id(expense.getId())
                    .date(expense.getExpenseDate())
                    .moduleType("EXPENSE")
                    .partyName(expense.getPartyName())
                    .billNumber(expense.getExpenseNumber())
                    .itemsTotal(amount)
                    .paidAmount(paidAmount)
                    .paidCashAmount(paidCashAmount)
                    .paidOnlineAmount(paidOnlineAmount)
                    .pendingAmount(pendingAmount)
                    .paymentStatus(paymentStatus)
                    .paymentType(expense.getPaymentType() != null ? expense.getPaymentType().name() : "CASH")
                    .category(expense.getCategory())
                    .transactionType(expense.getTransactionType().name())
                    .build());
        }
        
        // Sort by date descending - handle null dates safely
        expenseEntries.sort(Comparator.comparing(
            CashInHandDto.ExpenseEntry::getDate, 
            Comparator.nullsLast(Comparator.reverseOrder())
        ));
        
        return expenseEntries;
    }
    
    private LocalDate[] calculateDateRange(LocalDate startDate, LocalDate endDate, String filterType) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveStartDate = startDate;
        LocalDate effectiveEndDate = endDate;
        
        if (filterType != null) {
            switch (filterType.toUpperCase()) {
                case "DAY":
                    effectiveStartDate = today;
                    effectiveEndDate = today;
                    break;
                case "WEEK":
                    effectiveStartDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    effectiveEndDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                    break;
                case "MONTH":
                    effectiveStartDate = today.withDayOfMonth(1);
                    effectiveEndDate = today.with(TemporalAdjusters.lastDayOfMonth());
                    break;
                case "YEAR":
                    effectiveStartDate = today.withDayOfYear(1);
                    effectiveEndDate = today.with(TemporalAdjusters.lastDayOfYear());
                    break;
                case "CUSTOM":
                default:
                    // Use provided dates or default to last 30 days
                    if (effectiveStartDate == null) {
                        effectiveStartDate = today.minusDays(30);
                    }
                    if (effectiveEndDate == null) {
                        effectiveEndDate = today;
                    }
                    break;
            }
        } else {
            // Default to last 30 days if no filter type
            if (effectiveStartDate == null) {
                effectiveStartDate = today.minusDays(30);
            }
            if (effectiveEndDate == null) {
                effectiveEndDate = today;
            }
        }
        
        return new LocalDate[]{effectiveStartDate, effectiveEndDate};
    }
}

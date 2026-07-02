package com.app.billing.service;

import com.app.billing.dao.ItemRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dao.ClientRepository;
import com.app.billing.dao.PurchaseBillRepository;
import com.app.billing.dto.ExpenseDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.dto.PurchaseBillDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Expense;
import com.app.billing.model.Item;
import com.app.billing.model.MasterData;
import com.app.billing.model.Client;
import com.app.billing.model.PurchaseBill;
import com.app.billing.service.ExpenseService;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseBillService {
    
    private final PurchaseBillRepository purchaseBillRepository;
    private final ClientRepository clientRepository;
    private final ItemRepository itemRepository;
    private final MasterDataRepository masterDataRepository;
    private final ExpenseService expenseService;
    private final AuditUtil auditUtil;
    
    @Transactional
    public PurchaseBillDto create(PurchaseBillDto dto) {
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        // Validate party type - SUPPLIER, CUSTOMER, or BOTH allowed for Purchase
        if (client.getPartyType() != null &&
            client.getPartyType() != Client.ClientType.SUPPLIER &&
            client.getPartyType() != Client.ClientType.CUSTOMER &&
            client.getPartyType() != Client.ClientType.BOTH) {
            throw new IllegalArgumentException("Client type must be SUPPLIER, CUSTOMER, or BOTH for Purchase. This client is of type: " + client.getPartyType());
        }
        
        // Validate: paidAmount or (paidCashAmount + paidOnlineAmount) cannot exceed total
        if (dto.getPaidCashAmount() != null || dto.getPaidOnlineAmount() != null) {
            BigDecimal cash = dto.getPaidCashAmount() != null ? dto.getPaidCashAmount() : BigDecimal.ZERO;
            BigDecimal online = dto.getPaidOnlineAmount() != null ? dto.getPaidOnlineAmount() : BigDecimal.ZERO;
            if (dto.getTotalAmount() != null && cash.add(online).compareTo(dto.getTotalAmount()) > 0) {
                throw new IllegalArgumentException("Cash + Online paid cannot exceed total bill amount.");
            }
        } else if (dto.getPaidAmount() != null && dto.getTotalAmount() != null && 
            dto.getPaidAmount().compareTo(dto.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Paid amount cannot exceed total bill amount.");
        }
        
        PurchaseBill bill = toEntity(dto);
        bill.setPartyName(client.getPartyName());
        bill.setPartyPhone(client.getPhone());
        
        if (bill.getBillDate() == null) {
            bill.setBillDate(LocalDate.now());
        }
        
        // Purchase Invoice Number is OPTIONAL - supplier's invoice number
        // Can be null or empty - no validation required
        
        // Auto-generate Purchase Entry No if not provided
        if (bill.getPurchaseEntryNo() == null || bill.getPurchaseEntryNo().trim().isEmpty()) {
            bill.setPurchaseEntryNo(generateNextPurchaseEntryNo());
        }
        
        // First populate item details from Item master (category, unit, itemCode)
        populateItemDetails(bill.getItems());
        
        // Then merge duplicate items (same itemCode) - increase quantity instead of creating new row
        bill.setItems(mergeDuplicateItems(bill.getItems()));
        
        // Calculate totals
        calculateTotals(bill);
        
        // Update item stock (increase stock for purchase)
        updateItemStock(bill.getItems(), true);
        
        auditUtil.setCreatedBy(bill);
        PurchaseBill saved = purchaseBillRepository.save(bill);
        
        // If payment status is PAID, create expense entry
        if (saved.getPaymentStatus() == PurchaseBill.PaymentStatus.PAID) {
            createExpenseFromPurchase(saved);
        }
        
        return toDto(saved);
    }
    
    public PurchaseBillDto findById(String id) {
        PurchaseBill bill = purchaseBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase bill not found with id: " + id));
        return toDto(bill);
    }
    
    @Transactional
    public PurchaseBillDto update(String id, PurchaseBillDto dto) {
        PurchaseBill existing = purchaseBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase bill not found with id: " + id));
        
        // Restore stock from old items
        updateItemStock(existing.getItems(), false);
        
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        // Validate party type - SUPPLIER, CUSTOMER, or BOTH allowed for Purchase
        if (client.getPartyType() != null &&
            client.getPartyType() != Client.ClientType.SUPPLIER &&
            client.getPartyType() != Client.ClientType.CUSTOMER &&
            client.getPartyType() != Client.ClientType.BOTH) {
            throw new IllegalArgumentException("Client type must be SUPPLIER, CUSTOMER, or BOTH for Purchase. This client is of type: " + client.getPartyType());
        }
        
        // Validate: cash + online cannot exceed total
        BigDecimal totalAmount = dto.getTotalAmount() != null ? dto.getTotalAmount() : existing.getTotalAmount();
        if (dto.getPaidCashAmount() != null || dto.getPaidOnlineAmount() != null) {
            BigDecimal cash = dto.getPaidCashAmount() != null ? dto.getPaidCashAmount() : BigDecimal.ZERO;
            BigDecimal online = dto.getPaidOnlineAmount() != null ? dto.getPaidOnlineAmount() : BigDecimal.ZERO;
            if (totalAmount != null && cash.add(online).compareTo(totalAmount) > 0) {
                throw new IllegalArgumentException("Cash + Online paid cannot exceed total bill amount.");
            }
        } else if (dto.getPaidAmount() != null && totalAmount != null && 
            dto.getPaidAmount().compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("Paid amount cannot exceed total bill amount.");
        }
        
        PurchaseBill bill = toEntity(dto);
        bill.setId(id);
        bill.setPartyName(client.getPartyName());
        bill.setPartyPhone(client.getPhone());
        
        // First populate item details from Item master (category, unit, itemCode)
        populateItemDetails(bill.getItems());
        
        // Then merge duplicate items (same itemCode) - increase quantity instead of creating new row
        bill.setItems(mergeDuplicateItems(bill.getItems()));
        
        // Calculate totals
        calculateTotals(bill);
        
        // Update item stock (increase stock for purchase)
        updateItemStock(bill.getItems(), true);
        
        // Check if payment status changed to PAID (from UNPAID or PARTIAL)
        boolean wasNotPaid = existing.getPaymentStatus() != PurchaseBill.PaymentStatus.PAID;
        boolean isNowPaid = bill.getPaymentStatus() == PurchaseBill.PaymentStatus.PAID;
        
        auditUtil.setUpdatedBy(bill);
        PurchaseBill saved = purchaseBillRepository.save(bill);
        
        // If payment status changed to PAID, create expense entry
        if (wasNotPaid && isNowPaid) {
            createExpenseFromPurchase(saved);
        }
        
        return toDto(saved);
    }
    
    public PageResponse<PurchaseBillDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        // Show UNPAID and PARTIAL purchases in Purchase module (not PAID)
        List<PurchaseBill> allBills = purchaseBillRepository.findAll();
        List<PurchaseBill> filteredBills = allBills.stream()
            .filter(bill -> bill.getPaymentStatus() == PurchaseBill.PaymentStatus.UNPAID || 
                            bill.getPaymentStatus() == PurchaseBill.PaymentStatus.PARTIAL)
            .collect(Collectors.toList());
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredBills.size());
        List<PurchaseBill> pagedBills = start < filteredBills.size() ? filteredBills.subList(start, end) : new ArrayList<>();
        
        Page<PurchaseBill> bills = new org.springframework.data.domain.PageImpl<>(pagedBills, pageable, filteredBills.size());
        return PaginationUtil.toPageResponse(bills.map(this::toDto));
    }
    
    public PageResponse<PurchaseBillDto> findByDateRange(LocalDate startDate, LocalDate endDate, int page, int size) {
        Pageable pageable = PaginationUtil.createPageable(page, size, "billDate", "desc");
        Page<PurchaseBill> bills = purchaseBillRepository.findByBillDateBetween(startDate, endDate, pageable);
        return PaginationUtil.toPageResponse(bills.map(this::toDto));
    }
    
    @Transactional
    public void delete(String id) {
        PurchaseBill bill = purchaseBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase bill not found with id: " + id));
        
        // Restore stock (decrease stock - reverse purchase)
        updateItemStock(bill.getItems(), false);
        
        purchaseBillRepository.delete(bill);
    }
    
    private void calculateTotals(PurchaseBill bill) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        
        for (PurchaseBill.PurchaseItem item : bill.getItems()) {
            // Convert integer quantity to BigDecimal for calculation
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
            BigDecimal itemAmount = item.getPrice().multiply(quantity);
            BigDecimal itemTax = BigDecimal.ZERO;
            
            // Convert integer taxPercent to BigDecimal for calculation
            if (item.getTaxPercent() != null && item.getTaxPercent() > 0) {
                BigDecimal taxPercent = BigDecimal.valueOf(item.getTaxPercent());
                itemTax = itemAmount.multiply(taxPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            
            item.setTaxAmount(itemTax);
            item.setAmount(itemAmount.add(itemTax));
            
            subtotal = subtotal.add(itemAmount);
            totalTax = totalTax.add(itemTax);
        }
        
        bill.setSubtotal(subtotal);
        bill.setTaxAmount(totalTax);
        BigDecimal totalAmount = subtotal.add(totalTax);
        bill.setTotalAmount(totalAmount);
        
        // Calculate outstanding amount for partial payments
        BigDecimal paidAmount = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal outstandingAmount = totalAmount.subtract(paidAmount);
        bill.setOutstandingAmount(outstandingAmount.compareTo(BigDecimal.ZERO) > 0 ? outstandingAmount : BigDecimal.ZERO);
        
        // Auto-update payment status based on paid amount
        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            bill.setPaymentStatus(PurchaseBill.PaymentStatus.UNPAID);
        } else if (paidAmount.compareTo(totalAmount) >= 0) {
            bill.setPaymentStatus(PurchaseBill.PaymentStatus.PAID);
        } else {
            bill.setPaymentStatus(PurchaseBill.PaymentStatus.PARTIAL);
        }
    }
    
    private List<PurchaseBill.PurchaseItem> mergeDuplicateItems(List<PurchaseBill.PurchaseItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Group by itemCode (preferred) or itemId (fallback) and merge quantities
        Map<String, PurchaseBill.PurchaseItem> mergedMap = new HashMap<>();
        
        for (PurchaseBill.PurchaseItem item : items) {
            // Use Item Code as primary key (since it's unique), fallback to Item ID
            String key;
            if (item.getItemCode() != null && !item.getItemCode().trim().isEmpty()) {
                key = "CODE:" + item.getItemCode().trim();
            } else if (item.getItemId() != null && !item.getItemId().trim().isEmpty()) {
                key = "ID:" + item.getItemId().trim();
            } else {
                // Skip items without both itemCode and itemId
                continue;
            }
            
            PurchaseBill.PurchaseItem existing = mergedMap.get(key);
            if (existing == null) {
                // First occurrence - add to map
                mergedMap.put(key, item);
            } else {
                // Merge: add quantities (integers), keep other fields from first item
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                // Recalculate amount for merged item
                BigDecimal baseAmount = existing.getPrice().multiply(BigDecimal.valueOf(existing.getQuantity()));
                BigDecimal taxAmount = baseAmount.multiply(BigDecimal.valueOf(existing.getTaxPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                existing.setTaxAmount(taxAmount);
                existing.setAmount(baseAmount.add(taxAmount));
            }
        }
        
        return new ArrayList<>(mergedMap.values());
    }
    
    private void updateItemStock(List<PurchaseBill.PurchaseItem> items, boolean isPurchase) {
        for (PurchaseBill.PurchaseItem item : items) {
            // Use Item Code to find item (since it's unique)
            Item itemEntity = null;
            if (item.getItemCode() != null && !item.getItemCode().trim().isEmpty()) {
                itemEntity = itemRepository.findByItemCode(item.getItemCode()).orElse(null);
            }
            // Fallback to Item ID if Item Code not found
            if (itemEntity == null && item.getItemId() != null) {
                itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
            }
            
            if (itemEntity != null) {
                if (itemEntity.getStockQuantity() == null) {
                    itemEntity.setStockQuantity(BigDecimal.ZERO);
                }
                
                // Purchase increases stock, deletion/update decreases stock
                // Convert integer quantity to BigDecimal
                BigDecimal quantityChange = BigDecimal.valueOf(item.getQuantity());
                if (!isPurchase) {
                    quantityChange = quantityChange.negate();
                }
                itemEntity.setStockQuantity(itemEntity.getStockQuantity().add(quantityChange));
                itemRepository.save(itemEntity);
            }
        }
    }
    
    /**
     * Populate item details (category, unit, itemCode) from Item master
     */
    private void populateItemDetails(List<PurchaseBill.PurchaseItem> items) {
        for (PurchaseBill.PurchaseItem item : items) {
            Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
            if (itemEntity != null) {
                // Set itemCode from Item master (if not already set)
                if (item.getItemCode() == null || item.getItemCode().trim().isEmpty()) {
                    item.setItemCode(itemEntity.getItemCode());
                }
                // Set category from Item master
                if (itemEntity.getCategory() != null) {
                    item.setCategory(itemEntity.getCategory());
                }
                // Set unit from Item master (read-only, not from dropdown)
                if (itemEntity.getUnit() != null) {
                    item.setUnit(itemEntity.getUnit());
                }
            }
        }
    }
    
    private String generateNextPurchaseEntryNo() {
        List<PurchaseBill> allBills = purchaseBillRepository.findAll();
        int maxNumber = 0;
        
        for (PurchaseBill bill : allBills) {
            if (bill.getPurchaseEntryNo() != null) {
                try {
                    // Extract number from "PE-001" format
                    String numStr = bill.getPurchaseEntryNo().replaceAll("PE-", "").replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        maxNumber = Math.max(maxNumber, Integer.parseInt(numStr));
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid numbers
                }
            }
        }
        
        return "PE-" + String.format("%03d", maxNumber + 1);
    }
    
    private void createExpenseFromPurchase(PurchaseBill purchaseBill) {
        // Check if expense already exists for this purchase (prevent duplicates)
        // ExpenseService already handles this check
        
        // Get a valid expense category from Master Data
        String expenseCategory = getValidExpenseCategory();
        if (expenseCategory == null) {
            log.warn("No expense categories found in Master Data. Skipping auto-expense creation for Purchase Bill: {}", 
                purchaseBill.getBillNumber());
            return; // Skip expense creation if no valid category exists
        }
        
        ExpenseDto expenseDto = new ExpenseDto();
        expenseDto.setExpenseDate(purchaseBill.getBillDate());
        expenseDto.setExpenseDetails("Purchase Bill: " + purchaseBill.getBillNumber() + 
            (purchaseBill.getPurchaseEntryNo() != null ? " (Entry: " + purchaseBill.getPurchaseEntryNo() + ")" : ""));
        expenseDto.setCategory(expenseCategory); // Use valid category from Master Data
        expenseDto.setPartyName(purchaseBill.getPartyName());
        expenseDto.setPurchaseBillId(purchaseBill.getId());
        expenseDto.setPaymentType(convertPaymentType(purchaseBill.getPaymentType()));
        expenseDto.setAmount(purchaseBill.getTotalAmount());
        expenseDto.setDescription("Auto-generated from Purchase Bill " + purchaseBill.getBillNumber() + 
            ". Reference: " + (purchaseBill.getPurchaseEntryNo() != null ? purchaseBill.getPurchaseEntryNo() : purchaseBill.getBillNumber()));
        
        try {
            expenseService.create(expenseDto);
        } catch (Exception e) {
            log.error("Failed to create auto-expense for Purchase Bill: {}. Error: {}", 
                purchaseBill.getBillNumber(), e.getMessage());
            // Don't throw - purchase is already saved, expense creation failure shouldn't fail the purchase
        }
    }
    
    /**
     * Get the first available expense category from Master Data
     * @return category name or null if none exists
     */
    private String getValidExpenseCategory() {
        List<MasterData> expenseCategories = masterDataRepository.findByTypeAndActive(
            MasterData.MasterDataType.EXPENSE_CATEGORY, true);
        
        if (expenseCategories.isEmpty()) {
            return null;
        }
        
        // Return the first active expense category
        return expenseCategories.get(0).getName();
    }
    
    private Expense.PaymentType convertPaymentType(PurchaseBill.PaymentType paymentType) {
        if (paymentType == null) {
            return Expense.PaymentType.CASH;
        }
        switch (paymentType) {
            case CASH:
                return Expense.PaymentType.CASH;
            case ONLINE:
                return Expense.PaymentType.ONLINE;
            case CHEQUE:
                return Expense.PaymentType.CHEQUE;
            default:
                return Expense.PaymentType.CASH;
        }
    }
    
    private PurchaseBill toEntity(PurchaseBillDto dto) {
        BigDecimal paidCashAmount;
        BigDecimal paidOnlineAmount;
        BigDecimal paidAmount;
        if (dto.getPaidCashAmount() != null || dto.getPaidOnlineAmount() != null) {
            paidCashAmount = dto.getPaidCashAmount() != null ? dto.getPaidCashAmount() : BigDecimal.ZERO;
            paidOnlineAmount = dto.getPaidOnlineAmount() != null ? dto.getPaidOnlineAmount() : BigDecimal.ZERO;
            paidAmount = paidCashAmount.add(paidOnlineAmount);
        } else {
            BigDecimal paid = dto.getPaidAmount() != null ? dto.getPaidAmount() : BigDecimal.ZERO;
            if (dto.getPaymentType() == PurchaseBill.PaymentType.CASH) {
                paidCashAmount = paid;
                paidOnlineAmount = BigDecimal.ZERO;
            } else {
                paidCashAmount = BigDecimal.ZERO;
                paidOnlineAmount = paid;
            }
            paidAmount = paid;
        }
        PurchaseBill bill = PurchaseBill.builder()
                .purchaseEntryNo(dto.getPurchaseEntryNo())
                .billNumber(dto.getBillNumber())
                .billDate(dto.getBillDate())
                .partyId(dto.getPartyId())
                .partyName(dto.getPartyName())
                .partyPhone(dto.getPartyPhone())
                .paymentType(dto.getPaymentType())
                .paymentStatus(dto.getPaymentStatus() != null ? dto.getPaymentStatus() : PurchaseBill.PaymentStatus.UNPAID)
                .onlinePaymentMethod(dto.getOnlinePaymentMethod())
                .onlinePaymentReference(dto.getOnlinePaymentReference())
                .paidAmount(paidAmount)
                .paidCashAmount(paidCashAmount)
                .paidOnlineAmount(paidOnlineAmount)
                .outstandingAmount(dto.getOutstandingAmount())
                .uploadedBillFile(dto.getUploadedBillFile())
                .notes(dto.getNotes())
                .build();
        
        if (dto.getItems() != null) {
            List<PurchaseBill.PurchaseItem> items = dto.getItems().stream()
                    .map(itemDto -> {
                        PurchaseBill.PurchaseItem item = new PurchaseBill.PurchaseItem();
                        item.setItemId(itemDto.getItemId());
                        item.setItemCode(itemDto.getItemCode());
                        item.setItemName(itemDto.getItemName());
                        item.setCategory(itemDto.getCategory());
                        // Convert Integer quantity (from DTO) to Integer (entity uses Integer)
                        item.setQuantity(itemDto.getQuantity());
                        item.setUnit(itemDto.getUnit());
                        item.setPrice(itemDto.getPrice());
                        // Convert Integer taxPercent (from DTO) to Integer (entity uses Integer)
                        item.setTaxPercent(itemDto.getTaxPercent());
                        return item;
                    })
                    .collect(Collectors.toList());
            bill.setItems(items);
        }
        
        return bill;
    }
    
    private PurchaseBillDto toDto(PurchaseBill bill) {
        PurchaseBillDto dto = new PurchaseBillDto();
        dto.setId(bill.getId());
        dto.setPurchaseEntryNo(bill.getPurchaseEntryNo());
        dto.setBillNumber(bill.getBillNumber());
        dto.setBillDate(bill.getBillDate());
        dto.setPartyId(bill.getPartyId());
        dto.setPartyName(bill.getPartyName());
        dto.setPartyPhone(bill.getPartyPhone());
        dto.setPaymentType(bill.getPaymentType());
        dto.setPaymentStatus(bill.getPaymentStatus());
        dto.setOnlinePaymentMethod(bill.getOnlinePaymentMethod());
        dto.setOnlinePaymentReference(bill.getOnlinePaymentReference());
        dto.setPaidAmount(bill.getPaidAmount());
        dto.setPaidCashAmount(bill.getPaidCashAmount());
        dto.setPaidOnlineAmount(bill.getPaidOnlineAmount());
        dto.setOutstandingAmount(bill.getOutstandingAmount());
        dto.setUploadedBillFile(bill.getUploadedBillFile());
        dto.setNotes(bill.getNotes());
        dto.setSubtotal(bill.getSubtotal());
        dto.setTaxAmount(bill.getTaxAmount());
        dto.setTotalAmount(bill.getTotalAmount());
        
        if (bill.getItems() != null) {
            List<PurchaseBillDto.PurchaseItemDto> items = bill.getItems().stream()
                    .map(item -> {
                        PurchaseBillDto.PurchaseItemDto itemDto = new PurchaseBillDto.PurchaseItemDto();
                        itemDto.setItemId(item.getItemId());
                        itemDto.setItemCode(item.getItemCode());
                        itemDto.setItemName(item.getItemName());
                        itemDto.setCategory(item.getCategory());
                        // Convert Integer quantity (from entity) to Integer (DTO uses Integer)
                        itemDto.setQuantity(item.getQuantity());
                        itemDto.setUnit(item.getUnit());
                        itemDto.setPrice(item.getPrice());
                        // Convert Integer taxPercent (from entity) to Integer (DTO uses Integer)
                        itemDto.setTaxPercent(item.getTaxPercent());
                        itemDto.setTaxAmount(item.getTaxAmount());
                        itemDto.setAmount(item.getAmount());
                        return itemDto;
                    })
                    .collect(Collectors.toList());
            dto.setItems(items);
        }
        
        dto.setLastUpdatedBy(bill.getLastUpdatedBy());
        return dto;
    }
}


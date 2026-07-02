package com.app.billing.service;

import com.app.billing.dao.InvoiceRepository;
import com.app.billing.dao.ItemRepository;
import com.app.billing.dao.ClientRepository;
import com.app.billing.dto.InvoiceDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Invoice;
import com.app.billing.model.Item;
import com.app.billing.model.Client;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.InvoiceNumberGenerator;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {
    
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final AuditUtil auditUtil;
    
    @Transactional
    public InvoiceDto create(InvoiceDto dto) {
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        // Validate client type - only CUSTOMER allowed for Estimate/Billing
        if (client.getPartyType() != null && client.getPartyType() != Client.ClientType.CUSTOMER) {
            throw new IllegalArgumentException("Only CUSTOMER clients can be used for Estimate/Billing. This client is of type: " + client.getPartyType());
        }
        
        // Validate partial payment: cash + online cannot exceed total; use receivedCashAmount/receivedOnlineAmount when PARTIAL
        BigDecimal totalForValidation = dto.getTotalAmount() != null ? dto.getTotalAmount() : dto.getSubtotal();
        if (dto.getModeOfPayment() == Invoice.PaymentMode.PARTIAL &&
            (dto.getReceivedCashAmount() != null || dto.getReceivedOnlineAmount() != null) && totalForValidation != null) {
            BigDecimal cash = dto.getReceivedCashAmount() != null ? dto.getReceivedCashAmount() : BigDecimal.ZERO;
            BigDecimal online = dto.getReceivedOnlineAmount() != null ? dto.getReceivedOnlineAmount() : BigDecimal.ZERO;
            if (cash.add(online).compareTo(totalForValidation) > 0) {
                throw new IllegalArgumentException("Cash + Online received cannot exceed total bill amount.");
            }
        } else if (dto.getPaidAmount() != null && totalForValidation != null && 
            dto.getPaidAmount().compareTo(totalForValidation) > 0) {
            throw new IllegalArgumentException("Received amount cannot exceed total bill amount.");
        }
        
        // Determine bill type (default to ESTIMATE if not specified)
        Invoice.BillType billType = dto.getBillType() != null ? dto.getBillType() : Invoice.BillType.ESTIMATE;
        
        // Handle Bill No: validate uniqueness if manually entered, or auto-generate if empty
        if (dto.getInvoiceNumber() == null || dto.getInvoiceNumber().trim().isEmpty()) {
            // Auto-generate Bill No for the bill type series
            dto.setInvoiceNumber(invoiceNumberGenerator.generateNextInvoiceNumber(billType));
        } else {
            // Manual Bill No entered - validate uniqueness within the same bill type series
            String manualBillNo = dto.getInvoiceNumber().trim();
            if (invoiceRepository.existsByInvoiceNumberAndBillType(manualBillNo, billType)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill No already exists.");
            }
            dto.setInvoiceNumber(manualBillNo);
        }
        
        Invoice invoice = toEntity(dto);
        invoice.setPartyName(client.getPartyName());
        
        // Set invoice date - use from DTO if provided, otherwise use current date
        if (invoice.getInvoiceDate() == null) {
            invoice.setInvoiceDate(java.time.LocalDate.now());
        }
        
        // Set invoice date-time - use from DTO if provided, otherwise use current timestamp
        if (dto.getInvoiceDateTime() != null) {
            invoice.setInvoiceDateTime(dto.getInvoiceDateTime());
        } else {
            invoice.setInvoiceDateTime(java.time.LocalDateTime.now());
        }
        
        // Set initial status to NORMAL
        if (invoice.getStatus() == null) {
            invoice.setStatus(Invoice.BillStatus.NORMAL);
        }
        
        // REMOVED: Stock validation that blocks billing
        // Now allowing negative stock - stock will be updated even if insufficient
        
        // Calculate totals
        calculateInvoiceTotals(invoice);
        
        // Deduct stock ONLY for GST bills (NOT for Estimates or DRAFT bills)
        // Estimates and DRAFT bills do not affect stock quantities
        // Handle gracefully if item is deleted - skip stock update but log warning
        // Use Item Code for stock reduction (since it's unique)
        // Treat null status as NORMAL for backward compatibility
        Invoice.BillStatus invoiceStatus = invoice.getStatus() != null ? invoice.getStatus() : Invoice.BillStatus.NORMAL;
        if (invoice.getBillType() != null && invoice.getBillType() == Invoice.BillType.GST &&
            invoiceStatus != Invoice.BillStatus.DRAFT) {
            for (Invoice.InvoiceItem item : invoice.getItems()) {
            try {
                Item itemEntity = null;
                // Try to find by Item Code first (preferred)
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
                    
                    // Update stock - allows negative values
                    BigDecimal newStock = itemEntity.getStockQuantity().subtract(item.getQuantity());
                    itemEntity.setStockQuantity(newStock);
                    itemRepository.save(itemEntity);
                } else {
                    // Item deleted - log warning but don't fail
                    System.out.println("Warning: Item " + (item.getItemCode() != null ? item.getItemCode() : item.getItemId()) + " not found, skipping stock update for bill creation");
                }
            } catch (Exception e) {
                // Item may be deleted - log but don't fail bill creation
                System.out.println("Warning: Failed to update stock for item " + (item.getItemCode() != null ? item.getItemCode() : item.getItemId()) + ": " + e.getMessage());
            }
            }
        }
        
        // Initialize stockRestored flag to false for new invoices
        invoice.setStockRestored(false);
        
        auditUtil.setCreatedBy(invoice);
        invoice = invoiceRepository.save(invoice);
        return toDto(invoice);
    }
    
    @Transactional
    public InvoiceDto update(String id, InvoiceDto dto) {
        Invoice existingInvoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        
        // Prevent editing returned invoices
        if (existingInvoice.getStatus() != null && existingInvoice.getStatus() == Invoice.BillStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot edit a returned invoice. Invoice " + existingInvoice.getInvoiceNumber() + " has been returned.");
        }
        
        // Store original values to detect changes
        BigDecimal originalTotalAmount = existingInvoice.getTotalAmount();
        List<Invoice.InvoiceItem> originalItems = new ArrayList<>(existingInvoice.getItems());
        
        // Restore stock from original invoice items ONLY for GST bills (not Estimates)
        // This handles quantity changes or item removals
        // Handle gracefully if item is deleted - skip stock restore but log warning
        if (existingInvoice.getBillType() != null && existingInvoice.getBillType() == Invoice.BillType.GST) {
            for (Invoice.InvoiceItem oldItem : originalItems) {
                try {
                    // Check if item exists before trying to restore stock
                    if (itemRepository.existsById(oldItem.getItemId())) {
                        itemService.updateStock(oldItem.getItemId(), oldItem.getQuantity(), true);
                    } else {
                        System.out.println("Warning: Item " + oldItem.getItemId() + " not found, skipping stock restore for bill update");
                    }
                } catch (Exception e) {
                    // Item may be deleted - log but don't fail bill update
                    System.out.println("Warning: Failed to restore stock for item " + oldItem.getItemId() + ": " + e.getMessage());
                }
            }
        }
        
        // Validate client type if party changed - only CUSTOMER allowed for Estimate/Billing
        if (dto.getPartyId() != null && !dto.getPartyId().equals(existingInvoice.getPartyId())) {
            Client client = clientRepository.findById(dto.getPartyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
            if (client.getPartyType() != null && client.getPartyType() != Client.ClientType.CUSTOMER) {
                throw new IllegalArgumentException("Only CUSTOMER clients can be used for Estimate/Billing. This client is of type: " + client.getPartyType());
            }
        }
        
        // Validate partial payment: cash + online cannot exceed total when PARTIAL
        BigDecimal itemsTotal = dto.getTotalAmount() != null ? dto.getTotalAmount() : (dto.getSubtotal() != null ? dto.getSubtotal() : existingInvoice.getSubtotal());
        if (dto.getModeOfPayment() == Invoice.PaymentMode.PARTIAL &&
            (dto.getReceivedCashAmount() != null || dto.getReceivedOnlineAmount() != null) && itemsTotal != null) {
            BigDecimal cash = dto.getReceivedCashAmount() != null ? dto.getReceivedCashAmount() : BigDecimal.ZERO;
            BigDecimal online = dto.getReceivedOnlineAmount() != null ? dto.getReceivedOnlineAmount() : BigDecimal.ZERO;
            if (cash.add(online).compareTo(itemsTotal) > 0) {
                throw new IllegalArgumentException("Cash + Online received cannot exceed total bill amount.");
            }
        } else if (dto.getPaidAmount() != null && itemsTotal != null && 
            dto.getPaidAmount().compareTo(itemsTotal) > 0) {
            throw new IllegalArgumentException("Received amount cannot exceed total bill amount.");
        }
        
        // Validate Bill No uniqueness if manually changed (per bill type series)
        Invoice.BillType billType = dto.getBillType() != null ? dto.getBillType() : existingInvoice.getBillType();
        if (dto.getInvoiceNumber() != null && !dto.getInvoiceNumber().trim().isEmpty()) {
            String newBillNo = dto.getInvoiceNumber().trim();
            // Check if Bill No changed and if it conflicts with existing bill in same series
            if (!newBillNo.equals(existingInvoice.getInvoiceNumber())) {
                if (invoiceRepository.existsByInvoiceNumberAndBillType(newBillNo, billType)) {
                    // Restore stock before throwing error
                    for (Invoice.InvoiceItem restoreItem : originalItems) {
                        try {
                            if (itemRepository.existsById(restoreItem.getItemId())) {
                                itemService.updateStock(restoreItem.getItemId(), restoreItem.getQuantity(), true);
                            }
                        } catch (Exception e) {
                            // Item may be deleted - log but continue
                            System.out.println("Warning: Failed to restore stock for item " + restoreItem.getItemId() + " during validation: " + e.getMessage());
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill No already exists.");
                }
            }
        } else if (dto.getInvoiceNumber() == null || dto.getInvoiceNumber().trim().isEmpty()) {
            // If Bill No is empty, auto-generate for the bill type
            dto.setInvoiceNumber(invoiceNumberGenerator.generateNextInvoiceNumber(billType));
        }
        
        // Update entity
        updateEntity(existingInvoice, dto);
        
        // Update invoice date-time - use from DTO if provided, otherwise keep existing or set to now
        if (dto.getInvoiceDateTime() != null) {
            existingInvoice.setInvoiceDateTime(dto.getInvoiceDateTime());
        } else if (existingInvoice.getInvoiceDateTime() == null) {
            existingInvoice.setInvoiceDateTime(java.time.LocalDateTime.now());
        }
        
        // REMOVED: Stock validation that blocks billing
        // Now allowing negative stock - stock will be updated even if insufficient
        
        // Recalculate totals
        calculateInvoiceTotals(existingInvoice);
        
        // Update stock for updated items ONLY for GST bills (NOT for Estimates or DRAFT bills)
        // Estimates and DRAFT bills do not affect stock quantities
        // Use Item Code for stock reduction (since it's unique)
        // Handle gracefully if item is deleted - skip stock update but log warning
        // Treat null status as NORMAL for backward compatibility
        Invoice.BillStatus currentStatus = existingInvoice.getStatus() != null ? existingInvoice.getStatus() : Invoice.BillStatus.NORMAL;
        if (existingInvoice.getBillType() != null && existingInvoice.getBillType() == Invoice.BillType.GST &&
            currentStatus != Invoice.BillStatus.DRAFT) {
            for (Invoice.InvoiceItem item : existingInvoice.getItems()) {
            try {
                Item itemEntity = null;
                // Try to find by Item Code first (preferred)
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
                    
                    // Update stock - allows negative values
                    BigDecimal newStock = itemEntity.getStockQuantity().subtract(item.getQuantity());
                    itemEntity.setStockQuantity(newStock);
                    itemRepository.save(itemEntity);
                } else {
                    // Item deleted - log warning but don't fail
                    System.out.println("Warning: Item " + (item.getItemCode() != null ? item.getItemCode() : item.getItemId()) + " not found, skipping stock update for bill update");
                }
            } catch (Exception e) {
                // Item may be deleted - log but don't fail bill update
                System.out.println("Warning: Failed to update stock for item " + (item.getItemCode() != null ? item.getItemCode() : item.getItemId()) + ": " + e.getMessage());
            }
            }
        }
        
        // Check if amount/rate was modified
        boolean amountChanged = originalTotalAmount.compareTo(existingInvoice.getTotalAmount()) != 0;
        boolean rateChanged = false;
        
        // Check if any item rate/amount was manually changed
        for (int i = 0; i < existingInvoice.getItems().size() && i < originalItems.size(); i++) {
            Invoice.InvoiceItem newItem = existingInvoice.getItems().get(i);
            Invoice.InvoiceItem oldItem = originalItems.get(i);
            
            if (newItem.getUnitPrice().compareTo(oldItem.getUnitPrice()) != 0 ||
                newItem.getTotalAmount().compareTo(oldItem.getTotalAmount()) != 0) {
                rateChanged = true;
                break;
            }
        }
        
        // Handle status transitions
        // Store original status before any changes
        Invoice.BillStatus originalStatus = existingInvoice.getStatus();
        
        // If amount or rate was modified, mark as CORRECTED (unless already set by frontend)
        if (dto.getStatus() == null || dto.getStatus() == Invoice.BillStatus.NORMAL) {
            if (amountChanged || rateChanged) {
                existingInvoice.setStatus(Invoice.BillStatus.CORRECTED);
                
                // Add audit log entry
                if (existingInvoice.getAuditLog() == null) {
                    existingInvoice.setAuditLog(new ArrayList<>());
                }
                // Get current user from security context, fallback to "system" if not available
                String currentUser = "system";
                String currentUserName = "System";
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                    currentUser = auth.getName();
                    if (auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                        currentUserName = auth.getAuthorities().iterator().next().getAuthority();
                    }
                }
                
                existingInvoice.getAuditLog().add(new Invoice.AuditLogEntry(
                        java.time.LocalDateTime.now(),
                        currentUser,
                        currentUserName,
                        "Bill corrected: Total changed from " + originalTotalAmount + " to " + existingInvoice.getTotalAmount()
                ));
            } else {
                // No amount/rate change - update status from DTO
                // This handles DRAFT → NORMAL transitions when clicking "Update Transaction"
                existingInvoice.setStatus(dto.getStatus() != null ? dto.getStatus() : Invoice.BillStatus.NORMAL);
            }
        } else {
            // Use status from DTO (frontend may have set it to CORRECTED or other)
            existingInvoice.setStatus(dto.getStatus());
        }
        
        // If transitioning from DRAFT to NORMAL/CORRECTED for GST bills, deduct stock
        // (Stock was not deducted when originally saved as DRAFT)
        if (originalStatus == Invoice.BillStatus.DRAFT && 
            existingInvoice.getStatus() != Invoice.BillStatus.DRAFT &&
            existingInvoice.getBillType() == Invoice.BillType.GST) {
            for (Invoice.InvoiceItem item : existingInvoice.getItems()) {
                try {
                    Item itemEntity = null;
                    if (item.getItemCode() != null && !item.getItemCode().trim().isEmpty()) {
                        itemEntity = itemRepository.findByItemCode(item.getItemCode()).orElse(null);
                    }
                    if (itemEntity == null && item.getItemId() != null) {
                        itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                    }
                    if (itemEntity != null) {
                        BigDecimal newStock = itemEntity.getStockQuantity().subtract(item.getQuantity());
                        itemEntity.setStockQuantity(newStock);
                        itemRepository.save(itemEntity);
                    }
                } catch (Exception e) {
                    System.out.println("Warning: Failed to deduct stock for item during DRAFT→NORMAL transition: " + e.getMessage());
                }
            }
        }
        
        auditUtil.setUpdatedBy(existingInvoice);
        existingInvoice = invoiceRepository.save(existingInvoice);
        return toDto(existingInvoice);
    }
    
    public InvoiceDto findById(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        return toDto(invoice);
    }
    
    public PageResponse<InvoiceDto> findAll(int page, int size, String sortBy, String sortDir) {
        return findAll(page, size, sortBy, sortDir, null, null, null, null);
    }
    
    public PageResponse<InvoiceDto> findAll(int page, int size, String sortBy, String sortDir, 
                                           String billType, String partyId, String invoiceNumber, String status) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<Invoice> invoices;
        
        // Apply filters
        if (billType != null && !billType.isEmpty()) {
            Invoice.BillType type = "GST".equalsIgnoreCase(billType) ? Invoice.BillType.GST : Invoice.BillType.ESTIMATE;
            if (partyId != null && !partyId.isEmpty()) {
                // Filter by both bill type and party
                List<Invoice> filtered = invoiceRepository.findByBillType(type).stream()
                    .filter(inv -> inv.getPartyId().equals(partyId))
                    .filter(inv -> invoiceNumber == null || invoiceNumber.isEmpty() || 
                            (inv.getInvoiceNumber() != null && 
                             inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase())))
                    .filter(inv -> status == null || status.isEmpty() || 
                            (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                    .collect(Collectors.toList());
                // Convert to page manually
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), filtered.size());
                List<Invoice> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();
                invoices = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
            } else {
                // Filter by bill type only
                List<Invoice> filtered = invoiceRepository.findByBillType(type).stream()
                    .filter(inv -> invoiceNumber == null || invoiceNumber.isEmpty() || 
                            (inv.getInvoiceNumber() != null && 
                             inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase())))
                    .filter(inv -> status == null || status.isEmpty() || 
                            (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                    .collect(Collectors.toList());
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), filtered.size());
                List<Invoice> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();
                invoices = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
            }
        } else if (partyId != null && !partyId.isEmpty()) {
            // Filter by party only
            List<Invoice> filtered = invoiceRepository.findByPartyId(partyId).stream()
                .filter(inv -> invoiceNumber == null || invoiceNumber.isEmpty() || 
                        (inv.getInvoiceNumber() != null && 
                         inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase())))
                .filter(inv -> status == null || status.isEmpty() || 
                        (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                .collect(Collectors.toList());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<Invoice> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();
            invoices = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
        } else if (invoiceNumber != null && !invoiceNumber.isEmpty()) {
            // Filter by invoice number only - need to search all
            List<Invoice> allInvoices = invoiceRepository.findAll();
            List<Invoice> filtered = allInvoices.stream()
                .filter(inv -> inv.getInvoiceNumber() != null && 
                        inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase()))
                .filter(inv -> status == null || status.isEmpty() || 
                        (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                .collect(Collectors.toList());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<Invoice> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();
            invoices = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
        } else {
            // No filters or only status filter
            if (status != null && !status.isEmpty()) {
                List<Invoice> allInvoices = invoiceRepository.findAll();
                List<Invoice> filtered = allInvoices.stream()
                    .filter(inv -> inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), filtered.size());
                List<Invoice> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();
                invoices = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
            } else {
                // No filters - return all
                invoices = invoiceRepository.findAll(pageable);
            }
        }
        
        return PaginationUtil.toPageResponse(invoices.map(this::toDto));
    }
    
    /**
     * Get all invoices matching filters for Excel export (no pagination).
     * Reuses same filter logic as findAll. Memory-safe: streams to Excel.
     */
    public List<InvoiceDto> findAllForExport(String billType, String partyId, String invoiceNumber, String status) {
        List<Invoice> filtered;
        if (billType != null && !billType.isEmpty()) {
            Invoice.BillType type = "GST".equalsIgnoreCase(billType) ? Invoice.BillType.GST : Invoice.BillType.ESTIMATE;
            filtered = invoiceRepository.findByBillType(type).stream()
                    .filter(inv -> partyId == null || partyId.isEmpty() || (inv.getPartyId() != null && inv.getPartyId().equals(partyId)))
                    .filter(inv -> invoiceNumber == null || invoiceNumber.isEmpty() || 
                            (inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase())))
                    .filter(inv -> status == null || status.isEmpty() || 
                            (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                    .collect(Collectors.toList());
        } else if (partyId != null && !partyId.isEmpty()) {
            filtered = invoiceRepository.findByPartyId(partyId).stream()
                    .filter(inv -> invoiceNumber == null || invoiceNumber.isEmpty() || 
                            (inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase())))
                    .filter(inv -> status == null || status.isEmpty() || 
                            (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                    .collect(Collectors.toList());
        } else if (invoiceNumber != null && !invoiceNumber.isEmpty()) {
            filtered = invoiceRepository.findAll().stream()
                    .filter(inv -> inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(invoiceNumber.toLowerCase()))
                    .filter(inv -> status == null || status.isEmpty() || 
                            (inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status)))
                    .collect(Collectors.toList());
        } else if (status != null && !status.isEmpty()) {
            filtered = invoiceRepository.findAll().stream()
                    .filter(inv -> inv.getStatus() != null && inv.getStatus().toString().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        } else {
            filtered = invoiceRepository.findAll();
        }
        // Sort by invoiceDate desc
        filtered.sort((a, b) -> {
            LocalDate da = a.getInvoiceDate();
            LocalDate db = b.getInvoiceDate();
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });
        log.info("Invoice export: {} invoices for Excel", filtered.size());
        return filtered.stream().map(this::toDto).collect(Collectors.toList());
    }
    
    @Transactional
    public void delete(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        
        // Restore stock ONLY for GST bills (Estimates never deducted stock, so nothing to restore)
        // Handle gracefully if item is deleted - skip stock restore but log warning
        if (invoice.getBillType() != null && invoice.getBillType() == Invoice.BillType.GST) {
            for (Invoice.InvoiceItem item : invoice.getItems()) {
                try {
                    // Check if item exists before trying to restore stock
                    if (itemRepository.existsById(item.getItemId())) {
                        itemService.updateStock(item.getItemId(), item.getQuantity(), true);
                    } else {
                        System.out.println("Warning: Item " + item.getItemId() + " not found, skipping stock restore for bill deletion");
                    }
                } catch (Exception e) {
                    // Item may be deleted - log but don't fail bill deletion
                    System.out.println("Warning: Failed to restore stock for item " + item.getItemId() + " during bill deletion: " + e.getMessage());
                }
            }
        }
        
        invoiceRepository.deleteById(id);
    }
    
    @Transactional
    public void deleteByInvoiceNumber(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with invoice number: " + invoiceNumber));
        
        // Restore stock ONLY for GST bills (Estimates never deducted stock, so nothing to restore)
        // Handle gracefully if item is deleted - skip stock restore but log warning
        if (invoice.getBillType() != null && invoice.getBillType() == Invoice.BillType.GST) {
            for (Invoice.InvoiceItem item : invoice.getItems()) {
                try {
                    // Check if item exists before trying to restore stock
                    if (itemRepository.existsById(item.getItemId())) {
                        itemService.updateStock(item.getItemId(), item.getQuantity(), true);
                    } else {
                        System.out.println("Warning: Item " + item.getItemId() + " not found, skipping stock restore for bill deletion");
                    }
                } catch (Exception e) {
                    // Item may be deleted - log but don't fail bill deletion
                    System.out.println("Warning: Failed to restore stock for item " + item.getItemId() + " during bill deletion: " + e.getMessage());
                }
            }
        }
        
        invoiceRepository.deleteById(invoice.getId());
    }
    
    /**
     * Return an invoice - updates all fields, sets status to RETURNED and restores stock
     * Only allowed for GST invoices (not Estimates)
     * Can only be called during edit (not creation)
     * Stock is restored only once per invoice
     * 
     * @param id Invoice ID
     * @param dto InvoiceDto containing all updated fields (Notes, Date, etc.)
     * @return Updated InvoiceDto with RETURNED status
     */
    @Transactional
    public InvoiceDto returnInvoice(String id, InvoiceDto dto) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        
        // Prevent editing returned invoices
        if (invoice.getStatus() != null && invoice.getStatus() == Invoice.BillStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot return an invoice that has already been returned. Invoice " + invoice.getInvoiceNumber() + " has been returned.");
        }
        
        // Only GST invoices can be returned (Estimates don't affect stock)
        Invoice.BillType billType = dto.getBillType() != null ? dto.getBillType() : invoice.getBillType();
        if (billType == null || billType != Invoice.BillType.GST) {
            throw new IllegalArgumentException("Only GST invoices can be returned. This invoice is of type: " + 
                    (billType != null ? billType : "UNKNOWN"));
        }
        
        // Check if stock has already been restored (prevent double restoration)
        if (invoice.getStockRestored() != null && invoice.getStockRestored()) {
            throw new IllegalArgumentException("Stock for invoice " + invoice.getInvoiceNumber() + " has already been restored.");
        }
        
        // Store original items for stock restoration (before updating invoice)
        List<Invoice.InvoiceItem> originalItems = new ArrayList<>(invoice.getItems());
        
        // First, update the invoice with all fields from DTO (Notes, Date, etc.)
        // This ensures all form changes are saved
        updateEntity(invoice, dto);
        
        // Update invoice date-time if provided
        if (dto.getInvoiceDateTime() != null) {
            invoice.setInvoiceDateTime(dto.getInvoiceDateTime());
        }
        
        // Recalculate totals to ensure consistency
        calculateInvoiceTotals(invoice);
        
        // Restore stock for all items from the ORIGINAL invoice (before any updates)
        // This ensures we restore the correct quantities that were originally deducted
        for (Invoice.InvoiceItem item : originalItems) {
            try {
                Item itemEntity = null;
                // Try to find by Item Code first (preferred)
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
                    // Restore stock by adding the quantity back
                    BigDecimal newStock = itemEntity.getStockQuantity().add(item.getQuantity());
                    itemEntity.setStockQuantity(newStock);
                    itemRepository.save(itemEntity);
                } else {
                    System.out.println("Warning: Item " + (item.getItemCode() != null ? item.getItemCode() : item.getItemId()) + 
                            " not found, skipping stock restore for invoice return");
                }
            } catch (Exception e) {
                System.out.println("Warning: Failed to restore stock for item " + 
                        (item.getItemCode() != null ? item.getItemCode() : item.getItemId()) + 
                        " during invoice return: " + e.getMessage());
            }
        }
        
        // Set status to RETURNED
        invoice.setStatus(Invoice.BillStatus.RETURNED);
        
        // Mark stock as restored to prevent double restoration
        invoice.setStockRestored(true);
        
        // Update audit information
        auditUtil.setUpdatedBy(invoice);
        
        // Save and return
        invoice = invoiceRepository.save(invoice);
        return toDto(invoice);
    }
    
    private void calculateInvoiceTotals(Invoice invoice) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        
        for (Invoice.InvoiceItem item : invoice.getItems()) {
            // Use stored item snapshot (itemCode, itemName already in InvoiceItem)
            // If itemCode/itemName are missing, try to fetch from Item repository, but don't fail if item is deleted
            if (item.getItemCode() == null || item.getItemName() == null) {
                try {
                    Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                    if (itemEntity != null) {
                        item.setItemCode(itemEntity.getItemCode());
                        item.setItemName(itemEntity.getItemName());
                    }
                } catch (Exception e) {
                    // Item may be deleted - use stored snapshot or placeholder
                    // Log but don't fail
                    System.out.println("Warning: Item " + item.getItemId() + " not found, using stored snapshot");
                }
            }
            
            BigDecimal itemTotal = item.getQuantity().multiply(item.getUnitPrice());
            item.setTotalAmount(itemTotal);
            
            // For ESTIMATE: NO GST/TAX calculation
            // For GST: Calculate GST based on item's taxRate
            // Try to get taxRate from Item entity, but if item is deleted, use stored taxAmount or zero
            BigDecimal itemTax = BigDecimal.ZERO;
            if (invoice.getBillType() != null && invoice.getBillType() == Invoice.BillType.GST) {
                // Try to fetch taxRate from Item entity
                BigDecimal itemGstRate = BigDecimal.ZERO;
                try {
                    Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                    if (itemEntity != null && itemEntity.getTaxRate() != null) {
                        itemGstRate = itemEntity.getTaxRate();
                    } else if (item.getTaxAmount() != null && itemTotal.compareTo(BigDecimal.ZERO) > 0) {
                        // Item deleted - use stored taxAmount to infer rate, or use zero
                        // For existing bills with stored taxAmount, preserve it
                        itemTax = item.getTaxAmount();
                    }
                } catch (Exception e) {
                    // Item deleted - use stored taxAmount if available
                    if (item.getTaxAmount() != null) {
                        itemTax = item.getTaxAmount();
                    }
                    System.out.println("Warning: Item " + item.getItemId() + " not found for tax calculation, using stored tax amount");
                }
                
                // Calculate tax if we have a rate and haven't already set itemTax from stored value
                if (itemTax.compareTo(BigDecimal.ZERO) == 0 && itemGstRate.compareTo(BigDecimal.ZERO) > 0) {
                    itemTax = itemTotal.multiply(itemGstRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                }
            }
            // Estimate Bill: NO tax/GST (itemTax remains ZERO)
            
            item.setTaxAmount(itemTax);
            
            subtotal = subtotal.add(itemTotal);
            totalTax = totalTax.add(itemTax);
        }
        
        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(totalTax);
        
        // Apply discount with auto-calculation logic
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal discountPercent = BigDecimal.ZERO;
        
        if (invoice.getDiscountPercent() != null && invoice.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
            // If discount % is entered, calculate discount amount
            discountPercent = invoice.getDiscountPercent();
            discountAmount = subtotal.multiply(discountPercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            // If discount amount is entered, calculate discount %
            discountAmount = invoice.getDiscountAmount();
            if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                discountPercent = discountAmount.multiply(new BigDecimal("100")).divide(subtotal, 2, RoundingMode.HALF_UP);
            }
        }
        
        invoice.setDiscountAmount(discountAmount);
        invoice.setDiscountPercent(discountPercent);
        
        // Calculate GST ONLY for GST bills (NOT for Estimates)
        // Only CGST and SGST (IGST removed completely)
        BigDecimal gstAmount = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        
        if (invoice.getBillType() != null && invoice.getBillType() == Invoice.BillType.GST) {
            // GST Bill: Calculate GST based on item tax rates (already calculated above as totalTax)
            gstAmount = totalTax; // Use the total tax from items (which is GST for GST bills)
            
            // Split GST into CGST + SGST (50/50 split) - IGST removed
            cgst = gstAmount.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            sgst = gstAmount.subtract(cgst); // Handle rounding
        }
        // For ESTIMATE: All GST values remain ZERO (no GST calculation)
        
        invoice.setCgst(cgst);
        invoice.setSgst(sgst);
        
        // Calculate final total
        BigDecimal finalTotal = subtotal.subtract(discountAmount);
        if (invoice.getBillType() != null && invoice.getBillType() == Invoice.BillType.GST) {
            // GST Bill: Add GST
            finalTotal = finalTotal.add(gstAmount);
        }
        // For ESTIMATE or default: No GST added (finalTotal remains as subtotal - discount)
        
        invoice.setTotalAmount(finalTotal);
        
        // Calculate balance amount based on paid amount
        BigDecimal paidAmount = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal balanceAmount = finalTotal.subtract(paidAmount);
        invoice.setBalanceAmount(balanceAmount);
        
        // Auto-update payment status based on paid amount
        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            invoice.setPaymentStatus(Invoice.PaymentStatus.PENDING);
        } else if (paidAmount.compareTo(finalTotal) >= 0) {
            invoice.setPaymentStatus(Invoice.PaymentStatus.PAID);
        } else {
            invoice.setPaymentStatus(Invoice.PaymentStatus.PARTIAL);
        }
    }
    
    private Invoice toEntity(InvoiceDto dto) {
        List<Invoice.InvoiceItem> items = dto.getItems().stream()
                .map(itemDto -> {
                    Invoice.InvoiceItem item = new Invoice.InvoiceItem();
                    item.setItemId(itemDto.getItemId());
                    item.setItemCode(itemDto.getItemCode());
                    item.setItemName(itemDto.getItemName());
                    item.setQuantity(itemDto.getQuantity());
                    item.setUnitPrice(itemDto.getUnitPrice());
                    // taxRate removed from InvoiceItem
                    item.setTaxAmount(itemDto.getTaxAmount());
                    item.setTotalAmount(itemDto.getTotalAmount());
                    return item;
                })
                .collect(Collectors.toList());
        
        // Set receivedCashAmount / receivedOnlineAmount (never use totalAmount for cash flow)
        // Only use explicit split when mode is PARTIAL; otherwise derive from paidAmount + mode (so CASH+Partial with single Paid Amount is correct)
        BigDecimal receivedCashAmount;
        BigDecimal receivedOnlineAmount;
        BigDecimal paidAmount;
        if (dto.getModeOfPayment() == Invoice.PaymentMode.PARTIAL
                && (dto.getReceivedCashAmount() != null || dto.getReceivedOnlineAmount() != null)) {
            receivedCashAmount = dto.getReceivedCashAmount() != null ? dto.getReceivedCashAmount() : BigDecimal.ZERO;
            receivedOnlineAmount = dto.getReceivedOnlineAmount() != null ? dto.getReceivedOnlineAmount() : BigDecimal.ZERO;
            paidAmount = receivedCashAmount.add(receivedOnlineAmount);
        } else {
            BigDecimal paid = dto.getPaidAmount() != null ? dto.getPaidAmount() : BigDecimal.ZERO;
            if (dto.getModeOfPayment() == Invoice.PaymentMode.CASH) {
                receivedCashAmount = paid;
                receivedOnlineAmount = BigDecimal.ZERO;
            } else if (dto.getModeOfPayment() == Invoice.PaymentMode.ONLINE || dto.getModeOfPayment() == Invoice.PaymentMode.CHEQUE) {
                receivedCashAmount = BigDecimal.ZERO;
                receivedOnlineAmount = paid;
            } else {
                receivedCashAmount = BigDecimal.ZERO;
                receivedOnlineAmount = paid;
            }
            paidAmount = paid;
        }
        
        return Invoice.builder()
                .invoiceNumber(dto.getInvoiceNumber())
                .invoiceDate(dto.getInvoiceDate())
                .invoiceDateTime(dto.getInvoiceDateTime())
                .partyId(dto.getPartyId())
                .partyName(dto.getPartyName())
                .shippingAddress(dto.getShippingAddress())
                .shippingCity(dto.getShippingCity())
                .shippingState(dto.getShippingState())
                .shippingPincode(dto.getShippingPincode())
                .sameAsPartyAddress(dto.getSameAsPartyAddress())
                .items(items)
                .subtotal(dto.getSubtotal())
                .discountAmount(dto.getDiscountAmount())
                .discountPercent(dto.getDiscountPercent())
                .taxAmount(dto.getTaxAmount())
                .totalAmount(dto.getTotalAmount())
                .paymentStatus(dto.getPaymentStatus() != null ? dto.getPaymentStatus() : Invoice.PaymentStatus.PENDING)
                .paidAmount(paidAmount)
                .receivedCashAmount(receivedCashAmount)
                .receivedOnlineAmount(receivedOnlineAmount)
                .balanceAmount(dto.getBalanceAmount())
                .modeOfPayment(dto.getModeOfPayment())
                .onlinePaymentMethod(dto.getOnlinePaymentMethod())
                .onlinePaymentReference(dto.getOnlinePaymentReference())
                .notes(dto.getNotes())
                .billType(dto.getBillType() != null ? dto.getBillType() : Invoice.BillType.ESTIMATE)
                .status(dto.getStatus() != null ? dto.getStatus() : Invoice.BillStatus.NORMAL)
                .gstPercent(dto.getGstPercent())
                .cgst(dto.getCgst())
                .sgst(dto.getSgst())
                .stockRestored(dto.getStockRestored() != null ? dto.getStockRestored() : false)
                .build();
    }
    
    private void updateEntity(Invoice existingInvoice, InvoiceDto dto) {
        // Update Bill No if provided
        if (dto.getInvoiceNumber() != null && !dto.getInvoiceNumber().trim().isEmpty()) {
            existingInvoice.setInvoiceNumber(dto.getInvoiceNumber().trim());
        }
        
        existingInvoice.setInvoiceDate(dto.getInvoiceDate());
        if (dto.getInvoiceDateTime() != null) {
            existingInvoice.setInvoiceDateTime(dto.getInvoiceDateTime());
        }
        // PARTY NAME IMMUTABILITY: partyName should be stored at creation and never auto-updated
        // Only update partyName in these cases:
        // 1. Party was changed (different partyId) - lookup new party name
        // 2. partyName is explicitly provided in DTO
        boolean partyChanged = dto.getPartyId() != null && 
                              !dto.getPartyId().equals(existingInvoice.getPartyId());
        
        existingInvoice.setPartyId(dto.getPartyId());
        
        if (dto.getPartyName() != null && !dto.getPartyName().trim().isEmpty()) {
            // Explicit partyName provided - use it
            existingInvoice.setPartyName(dto.getPartyName());
        } else if (partyChanged && dto.getPartyId() != null) {
            // Party was changed - lookup the new party's name
            try {
                Client client = clientRepository.findById(dto.getPartyId()).orElse(null);
                if (client != null) {
                    existingInvoice.setPartyName(client.getPartyName());
                }
            } catch (Exception e) {
                // Keep existing partyName if lookup fails
                System.out.println("Warning: Failed to lookup party name for partyId: " + dto.getPartyId());
            }
        }
        // If party not changed and no explicit partyName - keep existing partyName (immutable)
        
        // Update shipping address
        existingInvoice.setShippingAddress(dto.getShippingAddress());
        existingInvoice.setShippingCity(dto.getShippingCity());
        existingInvoice.setShippingState(dto.getShippingState());
        existingInvoice.setShippingPincode(dto.getShippingPincode());
        existingInvoice.setSameAsPartyAddress(dto.getSameAsPartyAddress());
        
        existingInvoice.setNotes(dto.getNotes());
        existingInvoice.setBillType(dto.getBillType());
        // isInterState removed (IGST removed)
        existingInvoice.setDiscountPercent(dto.getDiscountPercent());
        existingInvoice.setDiscountAmount(dto.getDiscountAmount());
        existingInvoice.setPaymentStatus(dto.getPaymentStatus() != null ? dto.getPaymentStatus() : existingInvoice.getPaymentStatus());
        
        // CRITICAL FIX: Payment handling with preservation of existing cash/online amounts
        // When updating payments, we must preserve existing amounts and only add new payments
        // to the appropriate bucket (cash or online) based on the current payment mode.
        BigDecimal existingCash = existingInvoice.getReceivedCashAmount() != null ? existingInvoice.getReceivedCashAmount() : BigDecimal.ZERO;
        BigDecimal existingOnline = existingInvoice.getReceivedOnlineAmount() != null ? existingInvoice.getReceivedOnlineAmount() : BigDecimal.ZERO;
        BigDecimal existingPaid = existingCash.add(existingOnline);
        
        if (dto.getModeOfPayment() == Invoice.PaymentMode.PARTIAL
                && (dto.getReceivedCashAmount() != null || dto.getReceivedOnlineAmount() != null)) {
            // EXPLICIT SPLIT: User provides both cash and online amounts directly
            // This is used when user specifies the exact split (e.g., Cash: 400, Online: 300)
            BigDecimal cash = dto.getReceivedCashAmount() != null ? dto.getReceivedCashAmount() : BigDecimal.ZERO;
            BigDecimal online = dto.getReceivedOnlineAmount() != null ? dto.getReceivedOnlineAmount() : BigDecimal.ZERO;
            existingInvoice.setReceivedCashAmount(cash);
            existingInvoice.setReceivedOnlineAmount(online);
            existingInvoice.setPaidAmount(cash.add(online));
        } else if (dto.getPaidAmount() != null && dto.getModeOfPayment() != null) {
            BigDecimal newTotalPaid = dto.getPaidAmount();
            BigDecimal additionalPayment = newTotalPaid.subtract(existingPaid);
            
            // ADDITIVE PAYMENT: Add the additional amount to the appropriate bucket
            // This preserves existing cash/online amounts when completing partial payments
            if (additionalPayment.compareTo(BigDecimal.ZERO) > 0) {
                // New payment being added
                if (dto.getModeOfPayment() == Invoice.PaymentMode.CASH) {
                    existingInvoice.setReceivedCashAmount(existingCash.add(additionalPayment));
                    existingInvoice.setReceivedOnlineAmount(existingOnline);
                } else if (dto.getModeOfPayment() == Invoice.PaymentMode.ONLINE || dto.getModeOfPayment() == Invoice.PaymentMode.CHEQUE) {
                    existingInvoice.setReceivedCashAmount(existingCash);
                    existingInvoice.setReceivedOnlineAmount(existingOnline.add(additionalPayment));
                } else {
                    // Default: add to online
                    existingInvoice.setReceivedCashAmount(existingCash);
                    existingInvoice.setReceivedOnlineAmount(existingOnline.add(additionalPayment));
                }
            } else if (existingPaid.compareTo(BigDecimal.ZERO) == 0) {
                // FRESH PAYMENT: No existing payments, assign full amount to mode
                if (dto.getModeOfPayment() == Invoice.PaymentMode.CASH) {
                    existingInvoice.setReceivedCashAmount(newTotalPaid);
                    existingInvoice.setReceivedOnlineAmount(BigDecimal.ZERO);
                } else if (dto.getModeOfPayment() == Invoice.PaymentMode.ONLINE || dto.getModeOfPayment() == Invoice.PaymentMode.CHEQUE) {
                    existingInvoice.setReceivedCashAmount(BigDecimal.ZERO);
                    existingInvoice.setReceivedOnlineAmount(newTotalPaid);
                } else {
                    existingInvoice.setReceivedCashAmount(BigDecimal.ZERO);
                    existingInvoice.setReceivedOnlineAmount(newTotalPaid);
                }
            }
            // If additionalPayment <= 0 and existingPaid > 0, preserve existing split
            existingInvoice.setPaidAmount(newTotalPaid);
        } else if (dto.getPaidAmount() != null) {
            existingInvoice.setPaidAmount(dto.getPaidAmount());
        }
        if (dto.getModeOfPayment() != null) {
            existingInvoice.setModeOfPayment(dto.getModeOfPayment());
        }
        // Update online payment fields
        if (dto.getOnlinePaymentMethod() != null) {
            existingInvoice.setOnlinePaymentMethod(dto.getOnlinePaymentMethod());
        }
        if (dto.getOnlinePaymentReference() != null) {
            existingInvoice.setOnlinePaymentReference(dto.getOnlinePaymentReference());
        }
        
        // Update items - populate itemCode and itemName if missing
        List<Invoice.InvoiceItem> items = dto.getItems().stream()
                .map(itemDto -> {
                    Invoice.InvoiceItem item = new Invoice.InvoiceItem();
                    item.setItemId(itemDto.getItemId());
                    
                    // Populate itemCode and itemName if missing
                    String itemCode = itemDto.getItemCode();
                    String itemName = itemDto.getItemName();
                    if ((itemCode == null || itemCode.trim().isEmpty() || 
                         itemName == null || itemName.trim().isEmpty()) && 
                        itemDto.getItemId() != null) {
                        try {
                            Item itemEntity = itemRepository.findById(itemDto.getItemId()).orElse(null);
                            if (itemEntity != null) {
                                if (itemCode == null || itemCode.trim().isEmpty()) {
                                    itemCode = itemEntity.getItemCode();
                                }
                                if (itemName == null || itemName.trim().isEmpty()) {
                                    itemName = itemEntity.getItemName();
                                }
                            }
                        } catch (Exception e) {
                            // Item may be deleted - use what's in DTO or leave as is
                            System.out.println("Warning: Item " + itemDto.getItemId() + " not found when updating invoice items");
                        }
                    }
                    
                    item.setItemCode(itemCode);
                    item.setItemName(itemName);
                    item.setQuantity(itemDto.getQuantity());
                    item.setUnitPrice(itemDto.getUnitPrice());
                    // taxRate removed from InvoiceItem
                    item.setTaxAmount(itemDto.getTaxAmount());
                    item.setTotalAmount(itemDto.getTotalAmount());
                    return item;
                })
                .collect(Collectors.toList());
        existingInvoice.setItems(items);
    }
    
    private InvoiceDto toDto(Invoice invoice) {
        List<InvoiceDto.InvoiceItemDto> items = invoice.getItems().stream()
                .map(item -> {
                    InvoiceDto.InvoiceItemDto itemDto = new InvoiceDto.InvoiceItemDto();
                    itemDto.setItemId(item.getItemId());
                    itemDto.setItemCode(item.getItemCode());
                    itemDto.setItemName(item.getItemName());
                    itemDto.setQuantity(item.getQuantity());
                    itemDto.setUnitPrice(item.getUnitPrice());
                    // taxRate removed from InvoiceItem
                    itemDto.setTaxAmount(item.getTaxAmount());
                    itemDto.setTotalAmount(item.getTotalAmount());
                    return itemDto;
                })
                .collect(Collectors.toList());
        
        InvoiceDto dto = new InvoiceDto();
        dto.setId(invoice.getId());
        dto.setInvoiceNumber(invoice.getInvoiceNumber());
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setInvoiceDateTime(invoice.getInvoiceDateTime());
        dto.setPartyId(invoice.getPartyId());
        dto.setPartyName(invoice.getPartyName());
        dto.setShippingAddress(invoice.getShippingAddress());
        dto.setShippingCity(invoice.getShippingCity());
        dto.setShippingState(invoice.getShippingState());
        dto.setShippingPincode(invoice.getShippingPincode());
        dto.setSameAsPartyAddress(invoice.getSameAsPartyAddress());
        dto.setItems(items);
        dto.setSubtotal(invoice.getSubtotal());
        dto.setDiscountAmount(invoice.getDiscountAmount());
        dto.setDiscountPercent(invoice.getDiscountPercent());
        dto.setTaxAmount(invoice.getTaxAmount());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setPaymentStatus(invoice.getPaymentStatus());
        dto.setPaidAmount(invoice.getPaidAmount());
        dto.setBalanceAmount(invoice.getBalanceAmount());
        dto.setReceivedCashAmount(invoice.getReceivedCashAmount());
        dto.setReceivedOnlineAmount(invoice.getReceivedOnlineAmount());
        dto.setModeOfPayment(invoice.getModeOfPayment());
        dto.setOnlinePaymentMethod(invoice.getOnlinePaymentMethod());
        dto.setOnlinePaymentReference(invoice.getOnlinePaymentReference());
        dto.setNotes(invoice.getNotes());
        dto.setBillType(invoice.getBillType());
        dto.setStatus(invoice.getStatus());
        dto.setGstPercent(invoice.getGstPercent());
        dto.setCgst(invoice.getCgst());
        dto.setSgst(invoice.getSgst());
        // IGST removed
        dto.setLastUpdatedBy(invoice.getLastUpdatedBy());
        dto.setAuditLog(invoice.getAuditLog());
        dto.setStockRestored(invoice.getStockRestored());
        return dto;
    }
}

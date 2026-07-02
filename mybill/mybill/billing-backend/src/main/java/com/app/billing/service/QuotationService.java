package com.app.billing.service;

import com.app.billing.dao.ItemRepository;
import com.app.billing.dao.ClientRepository;
import com.app.billing.dao.QuotationRepository;
import com.app.billing.dto.PageResponse;
import com.app.billing.dto.QuotationDto;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Item;
import com.app.billing.model.Client;
import com.app.billing.model.Quotation;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuotationService {
    
    private final QuotationRepository quotationRepository;
    private final ClientRepository clientRepository;
    private final ItemRepository itemRepository;
    private final AuditUtil auditUtil;
    
    @Transactional
    public QuotationDto create(QuotationDto dto) {
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        // Validate party type - only CUSTOMER parties allowed for Quotation
        if (client.getPartyType() != null && client.getPartyType() != Client.ClientType.CUSTOMER) {
            throw new IllegalArgumentException("Only CUSTOMER clients can be used for Quotation. This client is of type: " + client.getPartyType());
        }
        
        // Auto-generate quotation number if not provided
        if (dto.getQuotationNumber() == null || dto.getQuotationNumber().trim().isEmpty()) {
            dto.setQuotationNumber(generateNextQuotationNumber());
        } else {
            // Manual quotation number entered - validate uniqueness
            String manualQuotationNo = dto.getQuotationNumber().trim();
            if (quotationRepository.existsByQuotationNumber(manualQuotationNo)) {
                throw new ResourceAlreadyExistsException("Quotation number already exists: " + manualQuotationNo);
            }
            dto.setQuotationNumber(manualQuotationNo);
        }
        
        Quotation quotation = toEntity(dto);
        quotation.setPartyName(client.getPartyName());
        
        // Handle shipping address - if sameAsPartyAddress is true, populate from party
        if (Boolean.TRUE.equals(dto.getSameAsPartyAddress())) {
            quotation.setShippingAddress(client.getAddress());
            quotation.setShippingCity(client.getCity());
            quotation.setShippingState(client.getState());
            quotation.setShippingPincode(client.getPincode());
        }
        
        // Set quotation date - use from DTO if provided, otherwise use current date
        if (quotation.getQuotationDate() == null) {
            quotation.setQuotationDate(java.time.LocalDate.now());
        }
        
        // Set quotation date-time - use from DTO if provided, otherwise use current timestamp
        if (dto.getQuotationDateTime() != null) {
            quotation.setQuotationDateTime(dto.getQuotationDateTime());
        } else {
            quotation.setQuotationDateTime(LocalDateTime.now());
        }
        
        // Calculate totals (no stock deduction for quotations)
        calculateQuotationTotals(quotation);
        
        auditUtil.setCreatedBy(quotation);
        quotation = quotationRepository.save(quotation);
        return toDto(quotation);
    }
    
    @Transactional
    public QuotationDto update(String id, QuotationDto dto) {
        Quotation existingQuotation = quotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found with id: " + id));
        
        // Validate quotation number uniqueness if manually changed
        if (dto.getQuotationNumber() != null &&
            !dto.getQuotationNumber().equals(existingQuotation.getQuotationNumber())) {
            if (quotationRepository.existsByQuotationNumber(dto.getQuotationNumber())) {
                throw new ResourceAlreadyExistsException("Quotation number already exists: " + dto.getQuotationNumber());
            }
        }
        
        Client client = clientRepository.findById(dto.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + dto.getPartyId()));
        
        updateEntity(existingQuotation, dto);
        existingQuotation.setPartyName(client.getPartyName());
        
        // Handle shipping address - if sameAsPartyAddress is true, populate from party
        if (Boolean.TRUE.equals(dto.getSameAsPartyAddress())) {
            existingQuotation.setShippingAddress(client.getAddress());
            existingQuotation.setShippingCity(client.getCity());
            existingQuotation.setShippingState(client.getState());
            existingQuotation.setShippingPincode(client.getPincode());
        }
        
        // Recalculate totals
        calculateQuotationTotals(existingQuotation);
        
        auditUtil.setUpdatedBy(existingQuotation);
        existingQuotation = quotationRepository.save(existingQuotation);
        return toDto(existingQuotation);
    }
    
    public QuotationDto findById(String id) {
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found with id: " + id));
        return toDto(quotation);
    }
    
    public PageResponse<QuotationDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<Quotation> quotations = quotationRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(quotations.map(this::toDto));
    }
    
    public PageResponse<QuotationDto> search(String searchTerm, int page, int size, String sortBy, String sortDir) {
        List<Quotation> quotations = quotationRepository.searchQuotations(searchTerm);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), quotations.size());
        List<Quotation> pagedQuotations = quotations.subList(start, end);
        
        return PaginationUtil.toPageResponse(
                pagedQuotations.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                quotations.size()
        );
    }
    
    @Transactional
    public void delete(String id) {
        if (!quotationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Quotation not found with id: " + id);
        }
        // No stock restoration needed for quotations (they don't deduct stock)
        quotationRepository.deleteById(id);
    }
    
    private String generateNextQuotationNumber() {
        List<Quotation> quotations = quotationRepository.findAll();
        
        int maxSequence = 0;
        int maxPaddingLength = 3;
        String prefix = "QUO";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^" + prefix + "[-_]?(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE);
        
        for (Quotation quotation : quotations) {
            String quotNum = quotation.getQuotationNumber();
            if (quotNum != null) {
                java.util.regex.Matcher matcher = pattern.matcher(quotNum);
                if (matcher.matches()) {
                    try {
                        String numericPart = matcher.group(1);
                        int seq = Integer.parseInt(numericPart);
                        maxSequence = Math.max(maxSequence, seq);
                        maxPaddingLength = Math.max(maxPaddingLength, numericPart.length());
                    } catch (NumberFormatException e) {
                        // Ignore invalid numbers
                    }
                }
            }
        }
        
        int nextSequence = maxSequence + 1;
        return prefix + "-" + String.format("%0" + maxPaddingLength + "d", nextSequence);
    }
    
    private void calculateQuotationTotals(Quotation quotation) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        
        // Check if GST is required (default to true for backward compatibility)
        boolean gstRequired = quotation.getGstRequired() == null || Boolean.TRUE.equals(quotation.getGstRequired());
        
        for (Quotation.QuotationItem item : quotation.getItems()) {
            // Use stored item snapshot
            if (item.getItemCode() == null || item.getItemName() == null) {
                try {
                    Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                    if (itemEntity != null) {
                        item.setItemCode(itemEntity.getItemCode());
                        item.setItemName(itemEntity.getItemName());
                    }
                } catch (Exception e) {
                    System.out.println("Warning: Item " + item.getItemId() + " not found, using stored snapshot");
                }
            }
            
            BigDecimal itemTotal = item.getQuantity().multiply(item.getUnitPrice());
            item.setTotalAmount(itemTotal);
            
            // Only calculate tax if GST is required
            BigDecimal itemTax = BigDecimal.ZERO;
            if (gstRequired) {
                try {
                    Item itemEntity = itemRepository.findById(item.getItemId()).orElse(null);
                    if (itemEntity != null && itemEntity.getTaxRate() != null) {
                        itemTax = itemTotal.multiply(itemEntity.getTaxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    }
                } catch (Exception e) {
                    // Item may be deleted - use zero tax
                }
            }
            
            item.setTaxAmount(itemTax);
            subtotal = subtotal.add(itemTotal);
            totalTax = totalTax.add(itemTax);
        }
        
        quotation.setSubtotal(subtotal);
        quotation.setTaxAmount(totalTax);
        
        // Split GST into CGST and SGST (50/50) if GST is required
        if (gstRequired && totalTax.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cgst = totalTax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            BigDecimal sgst = totalTax.subtract(cgst); // Handles rounding
            quotation.setCgst(cgst);
            quotation.setSgst(sgst);
        } else {
            quotation.setCgst(BigDecimal.ZERO);
            quotation.setSgst(BigDecimal.ZERO);
        }
        
        // Apply discount
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal discountPercent = BigDecimal.ZERO;
        
        if (quotation.getDiscountPercent() != null && quotation.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
            discountPercent = quotation.getDiscountPercent();
            discountAmount = subtotal.multiply(discountPercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else if (quotation.getDiscountAmount() != null && quotation.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = quotation.getDiscountAmount();
            if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                discountPercent = discountAmount.multiply(new BigDecimal("100")).divide(subtotal, 2, RoundingMode.HALF_UP);
            }
        }
        
        quotation.setDiscountAmount(discountAmount);
        quotation.setDiscountPercent(discountPercent);
        
        // Calculate final total
        BigDecimal finalTotal;
        if (gstRequired) {
            // With GST: subtotal - discount + tax
            finalTotal = subtotal.subtract(discountAmount).add(totalTax);
        } else {
            // Without GST: subtotal - discount
            finalTotal = subtotal.subtract(discountAmount);
        }
        quotation.setTotalAmount(finalTotal);
    }
    
    private Quotation toEntity(QuotationDto dto) {
        List<Quotation.QuotationItem> items = dto.getItems().stream()
                .map(itemDto -> {
                    Quotation.QuotationItem item = new Quotation.QuotationItem();
                    item.setItemId(itemDto.getItemId());
                    item.setItemCode(itemDto.getItemCode());
                    item.setItemName(itemDto.getItemName());
                    item.setQuantity(itemDto.getQuantity());
                    item.setUnitPrice(itemDto.getUnitPrice());
                    item.setTaxAmount(itemDto.getTaxAmount());
                    item.setTotalAmount(itemDto.getTotalAmount());
                    return item;
                })
                .collect(Collectors.toList());
        
        return Quotation.builder()
                .quotationNumber(dto.getQuotationNumber())
                .quotationDate(dto.getQuotationDate())
                .quotationDateTime(dto.getQuotationDateTime())
                .partyId(dto.getPartyId())
                .partyName(dto.getPartyName())
                .shippingToPartyId(dto.getShippingToPartyId())
                .shippingToPartyName(dto.getShippingToPartyName())
                .shippingAddress(dto.getShippingAddress())
                .shippingCity(dto.getShippingCity())
                .shippingState(dto.getShippingState())
                .shippingPincode(dto.getShippingPincode())
                .sameAsPartyAddress(dto.getSameAsPartyAddress())
                .deliveryDate(dto.getDeliveryDate())
                .validTillDays(dto.getValidTillDays())
                .items(items)
                .subtotal(dto.getSubtotal())
                .discountAmount(dto.getDiscountAmount())
                .discountPercent(dto.getDiscountPercent())
                .taxAmount(dto.getTaxAmount())
                .cgst(dto.getCgst())
                .sgst(dto.getSgst())
                .totalAmount(dto.getTotalAmount())
                .notes(dto.getNotes())
                .gstRequired(dto.getGstRequired())
                .build();
    }
    
    private QuotationDto toDto(Quotation quotation) {
        List<QuotationDto.QuotationItemDto> items = quotation.getItems().stream()
                .map(item -> {
                    QuotationDto.QuotationItemDto itemDto = new QuotationDto.QuotationItemDto();
                    itemDto.setItemId(item.getItemId());
                    itemDto.setItemCode(item.getItemCode());
                    itemDto.setItemName(item.getItemName());
                    itemDto.setQuantity(item.getQuantity());
                    itemDto.setUnitPrice(item.getUnitPrice());
                    itemDto.setTaxAmount(item.getTaxAmount());
                    itemDto.setTotalAmount(item.getTotalAmount());
                    return itemDto;
                })
                .collect(Collectors.toList());
        
        QuotationDto dto = new QuotationDto();
        dto.setId(quotation.getId());
        dto.setQuotationNumber(quotation.getQuotationNumber());
        dto.setQuotationDate(quotation.getQuotationDate());
        dto.setQuotationDateTime(quotation.getQuotationDateTime());
        dto.setPartyId(quotation.getPartyId());
        dto.setPartyName(quotation.getPartyName());
        dto.setShippingToPartyId(quotation.getShippingToPartyId());
        dto.setShippingToPartyName(quotation.getShippingToPartyName());
        dto.setShippingAddress(quotation.getShippingAddress());
        dto.setShippingCity(quotation.getShippingCity());
        dto.setShippingState(quotation.getShippingState());
        dto.setShippingPincode(quotation.getShippingPincode());
        dto.setSameAsPartyAddress(quotation.getSameAsPartyAddress());
        dto.setDeliveryDate(quotation.getDeliveryDate());
        dto.setValidTillDays(quotation.getValidTillDays());
        dto.setItems(items);
        dto.setSubtotal(quotation.getSubtotal());
        dto.setDiscountAmount(quotation.getDiscountAmount());
        dto.setDiscountPercent(quotation.getDiscountPercent());
        dto.setTaxAmount(quotation.getTaxAmount());
        dto.setCgst(quotation.getCgst());
        dto.setSgst(quotation.getSgst());
        dto.setTotalAmount(quotation.getTotalAmount());
        dto.setNotes(quotation.getNotes());
        dto.setLastUpdatedBy(quotation.getLastUpdatedBy());
        dto.setGstRequired(quotation.getGstRequired());
        return dto;
    }
    
    private void updateEntity(Quotation quotation, QuotationDto dto) {
        quotation.setQuotationNumber(dto.getQuotationNumber());
        quotation.setQuotationDate(dto.getQuotationDate());
        quotation.setQuotationDateTime(dto.getQuotationDateTime());
        quotation.setPartyId(dto.getPartyId());
        quotation.setPartyName(dto.getPartyName());
        quotation.setShippingToPartyId(dto.getShippingToPartyId());
        quotation.setShippingToPartyName(dto.getShippingToPartyName());
        quotation.setShippingAddress(dto.getShippingAddress());
        quotation.setShippingCity(dto.getShippingCity());
        quotation.setShippingState(dto.getShippingState());
        quotation.setShippingPincode(dto.getShippingPincode());
        quotation.setSameAsPartyAddress(dto.getSameAsPartyAddress());
        quotation.setDeliveryDate(dto.getDeliveryDate());
        quotation.setValidTillDays(dto.getValidTillDays());
        quotation.setNotes(dto.getNotes());
        quotation.setGstRequired(dto.getGstRequired());
        
        // Update items
        List<Quotation.QuotationItem> items = dto.getItems().stream()
                .map(itemDto -> {
                    Quotation.QuotationItem item = new Quotation.QuotationItem();
                    item.setItemId(itemDto.getItemId());
                    item.setItemCode(itemDto.getItemCode());
                    item.setItemName(itemDto.getItemName());
                    item.setQuantity(itemDto.getQuantity());
                    item.setUnitPrice(itemDto.getUnitPrice());
                    item.setTaxAmount(itemDto.getTaxAmount());
                    item.setTotalAmount(itemDto.getTotalAmount());
                    return item;
                })
                .collect(Collectors.toList());
        quotation.setItems(items);
    }
}


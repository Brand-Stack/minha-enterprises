package com.app.billing.service;

import com.app.billing.dao.ItemRepository;
import com.app.billing.dao.MasterDataRepository;
import com.app.billing.dto.ItemDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Item;
import com.app.billing.model.MasterData;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.CodeGenerator;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService {
    
    private final ItemRepository itemRepository;
    private final MasterDataRepository masterDataRepository;
    private final CodeGenerator codeGenerator;
    private final AuditUtil auditUtil;
    
    @Transactional
    public ItemDto create(ItemDto dto) {
        // Validate Category from Master
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            validateMasterData(dto.getCategory(), MasterData.MasterDataType.ITEM_CATEGORY, "Category");
        }
        
        // Validate Unit from Master
        if (dto.getUnit() != null && !dto.getUnit().trim().isEmpty()) {
            validateMasterData(dto.getUnit(), MasterData.MasterDataType.ITEM_UNIT, "Unit");
        }
        
        // Case-insensitive item code uniqueness check
        if (dto.getItemCode() != null && !dto.getItemCode().trim().isEmpty()) {
            String itemCode = dto.getItemCode().trim();
            // Check if any item exists with the same code (case-insensitive)
            List<Item> existingItems = itemRepository.findAll();
            boolean codeExists = existingItems.stream()
                    .anyMatch(item -> item.getItemCode() != null && 
                            item.getItemCode().equalsIgnoreCase(itemCode));
            
            if (codeExists) {
                throw new ResourceAlreadyExistsException("Item code already exists.");
            }
        }
        
        // Check EN Code uniqueness if provided
        if (dto.getEnCode() != null && !dto.getEnCode().trim().isEmpty()) {
            if (itemRepository.existsByEnCode(dto.getEnCode().trim())) {
                throw new ResourceAlreadyExistsException("EN Code already exists.");
            }
        }
        
        if (dto.getItemCode() == null || dto.getItemCode().isEmpty()) {
            // Find the highest item code that matches the format "ITM###"
            String lastCode = itemRepository.findAll().stream()
                    .map(Item::getItemCode)
                    .filter(code -> code != null && code.startsWith("ITM") && code.length() >= 6)
                    .filter(code -> {
                        // Validate that the sequence part is numeric
                        String seq = code.substring(3);
                        return seq.matches("\\d+");
                    })
                    .max((code1, code2) -> {
                        // Compare numeric sequence parts
                        try {
                            int seq1 = Integer.parseInt(code1.substring(3));
                            int seq2 = Integer.parseInt(code2.substring(3));
                            return Integer.compare(seq1, seq2);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
            dto.setItemCode(codeGenerator.generateItemCode(lastCode));
        }
        
        if (dto.getStockQuantity() == null) {
            dto.setStockQuantity(java.math.BigDecimal.ZERO);
        }
        
        Item item = toEntity(dto);
        auditUtil.setCreatedBy(item);
        item = itemRepository.save(item);
        return toDto(item);
    }
    
    @Transactional
    public ItemDto update(String id, ItemDto dto) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));
        
        // Validate Category from Master
        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            validateMasterData(dto.getCategory(), MasterData.MasterDataType.ITEM_CATEGORY, "Category");
        }
        
        // Validate Unit from Master
        if (dto.getUnit() != null && !dto.getUnit().trim().isEmpty()) {
            validateMasterData(dto.getUnit(), MasterData.MasterDataType.ITEM_UNIT, "Unit");
        }
        
        // Case-insensitive item code uniqueness check (only if code changed)
        if (dto.getItemCode() != null && !dto.getItemCode().trim().isEmpty()) {
            String newItemCode = dto.getItemCode().trim();
            if (!newItemCode.equalsIgnoreCase(item.getItemCode())) {
                // Code changed - check for case-insensitive duplicate
                List<Item> existingItems = itemRepository.findAll();
                boolean codeExists = existingItems.stream()
                        .anyMatch(existingItem -> !existingItem.getId().equals(id) && 
                                existingItem.getItemCode() != null && 
                                existingItem.getItemCode().equalsIgnoreCase(newItemCode));
                
                if (codeExists) {
                    throw new ResourceAlreadyExistsException("Item code already exists.");
                }
            }
        }
        
        // Check EN Code uniqueness if provided and changed
        if (dto.getEnCode() != null && !dto.getEnCode().trim().isEmpty()) {
            String newEnCode = dto.getEnCode().trim();
            if (!newEnCode.equals(item.getEnCode())) {
                if (itemRepository.existsByEnCode(newEnCode)) {
                    throw new ResourceAlreadyExistsException("EN Code already exists.");
                }
            }
        }
        
        updateEntity(item, dto);
        auditUtil.setUpdatedBy(item);
        item = itemRepository.save(item);
        return toDto(item);
    }
    
    public ItemDto findById(String id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + id));
        return toDto(item);
    }
    
    public PageResponse<ItemDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<Item> items = itemRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(items.map(this::toDto));
    }
    
    public PageResponse<ItemDto> search(String searchTerm, int page, int size, String sortBy, String sortDir) {
        List<Item> items = itemRepository.searchItems(searchTerm);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), items.size());
        List<Item> pagedItems = items.subList(start, end);
        
        return PaginationUtil.toPageResponse(
                pagedItems.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                items.size()
        );
    }
    
    public List<ItemDto> findAllForExport() {
        return itemRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void delete(String id) {
        if (!itemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Item not found with id: " + id);
        }
        itemRepository.deleteById(id);
    }
    
    @Transactional
    public void updateStock(String itemId, java.math.BigDecimal quantity, boolean isInward) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + itemId));
        updateStockForItem(item, quantity, isInward);
    }
    
    /**
     * Update stock using Item Code (preferred method since Item Code is unique)
     */
    @Transactional
    public void updateStockByItemCode(String itemCode, java.math.BigDecimal quantity, boolean isInward) {
        Item item = itemRepository.findByItemCode(itemCode)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with code: " + itemCode));
        updateStockForItem(item, quantity, isInward);
    }
    
    private void updateStockForItem(Item item, java.math.BigDecimal quantity, boolean isInward) {
        if (item.getStockQuantity() == null) {
            item.setStockQuantity(java.math.BigDecimal.ZERO);
        }
        
        if (isInward) {
            item.setStockQuantity(item.getStockQuantity().add(quantity));
        } else {
            // REMOVED: Stock validation that blocks negative stock
            // Now allowing negative stock - directly subtract (can result in negative values)
            item.setStockQuantity(item.getStockQuantity().subtract(quantity));
        }
        
        // Update stock status after stock change
        updateStockStatus(item);
        itemRepository.save(item);
    }
    
    public ItemDto findByEnCode(String enCode) {
        Item item = itemRepository.findByEnCode(enCode)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with EN Code: " + enCode));
        return toDto(item);
    }
    
    private void validateMasterData(String value, MasterData.MasterDataType type, String fieldName) {
        List<MasterData> masterDataList = masterDataRepository.findByTypeAndActive(type, true);
        boolean exists = masterDataList.stream()
                .anyMatch(md -> md.getName().equalsIgnoreCase(value.trim()));
        if (!exists) {
            throw new ResourceNotFoundException(fieldName + " '" + value + "' not found in Master Data. Please create it in Master module first.");
        }
    }
    
    private Item toEntity(ItemDto dto) {
        return Item.builder()
                .itemCode(dto.getItemCode())
                .enCode(dto.getEnCode())
                .itemName(dto.getItemName())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .unit(dto.getUnit())
                .purchasePrice(dto.getPurchasePrice())
                .sellingPrice(dto.getSellingPrice())
                .stockQuantity(dto.getStockQuantity())
                .minStockLevel(dto.getMinStockLevel())
                .hsnCode(dto.getHsnCode())
                .taxRate(dto.getTaxRate())
                .customFields(dto.getCustomFields())
                .build();
    }
    
    private ItemDto toDto(Item item) {
        ItemDto dto = new ItemDto();
        dto.setId(item.getId());
        dto.setItemCode(item.getItemCode());
        dto.setEnCode(item.getEnCode());
        dto.setItemName(item.getItemName());
        dto.setDescription(item.getDescription());
        dto.setCategory(item.getCategory());
        dto.setUnit(item.getUnit());
        dto.setPurchasePrice(item.getPurchasePrice());
        dto.setSellingPrice(item.getSellingPrice());
        dto.setStockQuantity(item.getStockQuantity());
        dto.setMinStockLevel(item.getMinStockLevel());
        dto.setHsnCode(item.getHsnCode());
        dto.setTaxRate(item.getTaxRate());
        dto.setCustomFields(item.getCustomFields());
        dto.setLastUpdatedBy(item.getLastUpdatedBy());
        return dto;
    }
    
    private void updateEntity(Item item, ItemDto dto) {
        item.setItemCode(dto.getItemCode());
        item.setEnCode(dto.getEnCode());
        item.setItemName(dto.getItemName());
        item.setDescription(dto.getDescription());
        item.setCategory(dto.getCategory());
        item.setUnit(dto.getUnit());
        item.setPurchasePrice(dto.getPurchasePrice());
        item.setSellingPrice(dto.getSellingPrice());
        item.setStockQuantity(dto.getStockQuantity()); // FIX: Include stockQuantity in update
        item.setMinStockLevel(dto.getMinStockLevel());
        item.setHsnCode(dto.getHsnCode());
        item.setTaxRate(dto.getTaxRate());
        // Update stock status based on new stock quantity
        updateStockStatus(item);
    }
    
    private void updateStockStatus(Item item) {
        if (item.getStockQuantity() == null) {
            item.setStockQuantity(java.math.BigDecimal.ZERO);
        }
        if (item.getStockQuantity().compareTo(java.math.BigDecimal.ZERO) == 0) {
            // Out of stock - but Item model doesn't have stockStatus field yet
            // This can be tracked via stockQuantity = 0
        } else if (item.getMinStockLevel() != null && 
                   item.getStockQuantity().compareTo(item.getMinStockLevel()) < 0) {
            // Low stock
        } else {
            // In stock
        }
    }
}


package com.app.billing.controller;

import com.app.billing.dto.ItemDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
@Tag(name = "Item Management", description = "Item CRUD operations")
public class ItemController {
    
    private final ItemService itemService;
    
    @PostMapping
    @RequiresPermission(module = Modules.ITEMS, action = Modules.CREATE)
    @Operation(summary = "Create item", description = "Create a new item")
    public ResponseEntity<ItemDto> create(@Valid @RequestBody ItemDto dto) {
        return new ResponseEntity<>(itemService.create(dto), HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.ITEMS, action = Modules.EDIT)
    @Operation(summary = "Update item", description = "Update existing item")
    public ResponseEntity<ItemDto> update(@PathVariable String id, @Valid @RequestBody ItemDto dto) {
        return ResponseEntity.ok(itemService.update(id, dto));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get item by ID", description = "Retrieve item by ID")
    public ResponseEntity<ItemDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(itemService.findById(id));
    }
    
    @GetMapping("/en-code/{enCode}")
    @Operation(summary = "Get item by EN Code", description = "Retrieve item by EN Code")
    public ResponseEntity<ItemDto> findByEnCode(@PathVariable String enCode) {
        return ResponseEntity.ok(itemService.findByEnCode(enCode));
    }
    
    @GetMapping
    @RequiresPermission(module = Modules.ITEMS, action = Modules.VIEW)
    @Operation(summary = "Get all items", description = "Retrieve all items with pagination")
    public ResponseEntity<PageResponse<ItemDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(itemService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search items", description = "Search items by name, code, or category")
    public ResponseEntity<PageResponse<ItemDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(itemService.search(searchTerm, page, size, sortBy, sortDir));
    }
    
    @GetMapping("/export")
    @RequiresPermission(module = Modules.ITEMS, action = Modules.EXPORT)
    @Operation(summary = "Export items", description = "Export all items for Excel")
    public ResponseEntity<List<ItemDto>> exportItems() {
        return ResponseEntity.ok(itemService.findAllForExport());
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.ITEMS, action = Modules.DELETE)
    @Operation(summary = "Delete item", description = "Soft delete item")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        itemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}


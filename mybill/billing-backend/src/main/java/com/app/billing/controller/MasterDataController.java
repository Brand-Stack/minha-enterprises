package com.app.billing.controller;

import com.app.billing.dto.MasterDataDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.model.MasterData;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/master-data")
@RequiredArgsConstructor
@Tag(name = "Master Data Management", description = "Master data CRUD operations")
public class MasterDataController {
    
    private final MasterDataService masterDataService;
    
    @PostMapping
    @RequiresPermission(module = Modules.MASTER_DATA, action = Modules.CREATE)
    @Operation(summary = "Create master data", description = "Create a new master data entry")
    public ResponseEntity<MasterDataDto> create(@Valid @RequestBody MasterDataDto dto) {
        return new ResponseEntity<>(masterDataService.create(dto), HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.MASTER_DATA, action = Modules.EDIT)
    @Operation(summary = "Update master data", description = "Update existing master data")
    public ResponseEntity<MasterDataDto> update(@PathVariable String id, @Valid @RequestBody MasterDataDto dto) {
        return ResponseEntity.ok(masterDataService.update(id, dto));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get master data by ID", description = "Retrieve master data by ID")
    public ResponseEntity<MasterDataDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(masterDataService.findById(id));
    }
    
    @GetMapping
    @Operation(summary = "Get all master data", description = "Retrieve all master data with pagination")
    public ResponseEntity<PageResponse<MasterDataDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(masterDataService.findAll(page, size, sortBy, sortDir));
    }
    
    @GetMapping("/type/{type}")
    @Operation(summary = "Get master data by type", description = "Retrieve all active master data of a specific type")
    public ResponseEntity<List<MasterDataDto>> findAllByType(@PathVariable MasterData.MasterDataType type) {
        return ResponseEntity.ok(masterDataService.findAllByType(type));
    }
    
    @GetMapping("/type/{type}/paginated")
    @Operation(summary = "Get master data by type (paginated)", description = "Retrieve master data of a specific type with pagination")
    public ResponseEntity<PageResponse<MasterDataDto>> findByType(
            @PathVariable MasterData.MasterDataType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(masterDataService.findByType(type, page, size, sortBy, sortDir));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search master data", description = "Search master data by term and type")
    public ResponseEntity<PageResponse<MasterDataDto>> search(
            @RequestParam String searchTerm,
            @RequestParam MasterData.MasterDataType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(masterDataService.search(searchTerm, type, page, size, sortBy, sortDir));
    }
    
    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.MASTER_DATA, action = Modules.DELETE)
    @Operation(summary = "Delete master data", description = "Delete master data by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        masterDataService.delete(id);
        return ResponseEntity.noContent().build();
    }
}


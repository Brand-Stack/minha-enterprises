package com.app.billing.controller;

import com.app.billing.dto.PageResponse;
import com.app.billing.dto.ZoneConfigurationDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.ZoneConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/zone-configurations")
@RequiredArgsConstructor
@Tag(name = "Zone Configuration", description = "Zone Configuration Management for Courier Rates")
public class ZoneConfigurationController {

    private final ZoneConfigurationService service;

    @PostMapping
    @RequiresPermission(module = Modules.ZONE_CONFIG, action = Modules.CREATE)
    @Operation(summary = "Create zone configuration")
    public ResponseEntity<ZoneConfigurationDto> create(@Valid @RequestBody ZoneConfigurationDto dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.ZONE_CONFIG, action = Modules.EDIT)
    @Operation(summary = "Update zone configuration")
    public ResponseEntity<ZoneConfigurationDto> update(@PathVariable String id,
            @Valid @RequestBody ZoneConfigurationDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get zone configuration by ID")
    public ResponseEntity<ZoneConfigurationDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active zone configurations, optionally filtered by rate type (EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass)")
    public ResponseEntity<List<ZoneConfigurationDto>> findAllActive(
            @RequestParam(required = false) String rateType) {
        if (rateType != null && !rateType.trim().isEmpty()) {
            return ResponseEntity.ok(service.findAllActiveByRateType(rateType));
        }
        return ResponseEntity.ok(service.findAllActive());
    }

    @GetMapping
    @RequiresPermission(module = Modules.ZONE_CONFIG, action = Modules.VIEW)
    @Operation(summary = "Get all zone configurations (paginated)")
    public ResponseEntity<PageResponse<ZoneConfigurationDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "zoneName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(service.findAll(page, size, sortBy, sortDir));
    }

    @GetMapping("/search")
    @Operation(summary = "Search zone configurations")
    public ResponseEntity<PageResponse<ZoneConfigurationDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "zoneName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(service.search(searchTerm, page, size, sortBy, sortDir));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.ZONE_CONFIG, action = Modules.DELETE)
    @Operation(summary = "Delete zone configuration")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

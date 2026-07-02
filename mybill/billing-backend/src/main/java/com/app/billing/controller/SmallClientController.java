package com.app.billing.controller;

import com.app.billing.dto.SmallClientDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.model.SmallClient;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.SmallClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/small-clients")
@RequiredArgsConstructor
@Tag(name = "Small Clients", description = "Retail / small client master CRUD")
public class SmallClientController {

    private final SmallClientService smallClientService;

    @PostMapping
    @RequiresPermission(module = Modules.SMALL_CLIENTS, action = Modules.CREATE)
    @Operation(summary = "Create small client")
    public ResponseEntity<SmallClientDto> create(@Valid @RequestBody SmallClientDto dto) {
        return new ResponseEntity<>(smallClientService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.SMALL_CLIENTS, action = Modules.EDIT)
    @Operation(summary = "Update small client")
    public ResponseEntity<SmallClientDto> update(@PathVariable String id, @Valid @RequestBody SmallClientDto dto) {
        return ResponseEntity.ok(smallClientService.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get small client by ID")
    public ResponseEntity<SmallClientDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(smallClientService.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.SMALL_CLIENTS, action = Modules.VIEW)
    @Operation(summary = "List small clients (paginated)")
    public ResponseEntity<PageResponse<SmallClientDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(smallClientService.findAll(page, size, sortBy, sortDir));
    }

    @GetMapping("/search")
    @Operation(summary = "Search small clients")
    public ResponseEntity<PageResponse<SmallClientDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(smallClientService.search(searchTerm, page, size, sortBy, sortDir));
    }

    @GetMapping("/type/{partyType}")
    @Operation(summary = "Small clients by type")
    public ResponseEntity<List<SmallClientDto>> findAllByPartyType(@PathVariable SmallClient.SmallClientType partyType) {
        return ResponseEntity.ok(smallClientService.findAllByPartyType(partyType));
    }

    @GetMapping("/export")
    @RequiresPermission(module = Modules.SMALL_CLIENTS, action = Modules.EXPORT)
    @Operation(summary = "Export all small clients")
    public ResponseEntity<List<SmallClientDto>> exportClients() {
        return ResponseEntity.ok(smallClientService.findAllForExport());
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.SMALL_CLIENTS, action = Modules.DELETE)
    @Operation(summary = "Delete small client")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        smallClientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.app.billing.controller;

import com.app.billing.dto.ClientDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.model.Client;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/clients", "/parties"})
@RequiredArgsConstructor
@Tag(name = "Client Management", description = "Client (customer/supplier) CRUD — /parties kept for backward compatibility")
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @RequiresPermission(module = Modules.CLIENTS, action = Modules.CREATE)
    @Operation(summary = "Create client")
    public ResponseEntity<ClientDto> create(@Valid @RequestBody ClientDto dto) {
        return new ResponseEntity<>(clientService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.CLIENTS, action = Modules.EDIT)
    @Operation(summary = "Update client")
    public ResponseEntity<ClientDto> update(@PathVariable String id, @Valid @RequestBody ClientDto dto) {
        return ResponseEntity.ok(clientService.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    public ResponseEntity<ClientDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(clientService.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.CLIENTS, action = Modules.VIEW)
    @Operation(summary = "List clients (paginated)")
    public ResponseEntity<PageResponse<ClientDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(clientService.findAll(page, size, sortBy, sortDir));
    }

    @GetMapping("/search")
    @Operation(summary = "Search clients")
    public ResponseEntity<PageResponse<ClientDto>> search(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(clientService.search(searchTerm, page, size, sortBy, sortDir));
    }

    @GetMapping("/type/{partyType}")
    @Operation(summary = "Clients by type")
    public ResponseEntity<List<ClientDto>> findAllByPartyType(@PathVariable Client.ClientType partyType) {
        return ResponseEntity.ok(clientService.findAllByPartyType(partyType));
    }

    @GetMapping("/for-purchase")
    @Operation(summary = "Clients usable for purchase")
    public ResponseEntity<List<ClientDto>> findAllForPurchase() {
        return ResponseEntity.ok(clientService.findAllForPurchase());
    }

    @GetMapping("/type/{partyType}/paginated")
    @Operation(summary = "Clients by type (paginated)")
    public ResponseEntity<PageResponse<ClientDto>> findByPartyType(
            @PathVariable Client.ClientType partyType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(clientService.findByPartyType(partyType, page, size, sortBy, sortDir));
    }

    @GetMapping("/export")
    @RequiresPermission(module = Modules.CLIENTS, action = Modules.EXPORT)
    @Operation(summary = "Export all clients")
    public ResponseEntity<List<ClientDto>> exportClients() {
        return ResponseEntity.ok(clientService.findAllForExport());
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.CLIENTS, action = Modules.DELETE)
    @Operation(summary = "Delete client")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

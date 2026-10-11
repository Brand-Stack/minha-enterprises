package com.app.billing.controller;

import com.app.billing.model.ModuleRegistry;
import com.app.billing.service.ModuleRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/module-registry")
@RequiredArgsConstructor
@Tag(name = "Module Registry", description = "Metadata catalogue of securable modules (Admin only)")
public class ModuleRegistryController {

    private final ModuleRegistryService moduleRegistryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List modules", description = "Return all registered modules for the entitlement matrix UI")
    public ResponseEntity<List<ModuleRegistry>> findAll() {
        return ResponseEntity.ok(moduleRegistryService.findAll());
    }
}

package com.app.billing.controller;

import com.app.billing.dto.EntitlementDto;
import com.app.billing.service.EntitlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/entitlements")
@RequiredArgsConstructor
@Tag(name = "Entitlement Management", description = "Permission matrix per category (Admin only)")
@PreAuthorize("hasRole('ADMIN')")
public class EntitlementController {

    private final EntitlementService entitlementService;

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get permission matrix for a category")
    public ResponseEntity<EntitlementDto> getByCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(entitlementService.getByCategoryId(categoryId));
    }

    @PutMapping
    @Operation(summary = "Save permission matrix for a category")
    public ResponseEntity<EntitlementDto> save(@Valid @RequestBody EntitlementDto dto) {
        return ResponseEntity.ok(entitlementService.save(dto));
    }
}

package com.app.billing.controller;

import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CompanySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/company-settings")
@RequiredArgsConstructor
@Tag(name = "Company Settings", description = "Company settings and print configuration")
public class CompanySettingsController {
    
    private final CompanySettingsService companySettingsService;
    
    @GetMapping
    @Operation(summary = "Get company settings", description = "Get current company settings")
    public ResponseEntity<CompanySettingsDto> getSettings() {
        return ResponseEntity.ok(companySettingsService.getSettings());
    }
    
    @PutMapping
    @RequiresPermission(module = Modules.SETTINGS, action = Modules.EDIT)
    @Operation(summary = "Save company settings", description = "Save or update company settings")
    public ResponseEntity<CompanySettingsDto> saveSettings(@RequestBody CompanySettingsDto dto) {
        return ResponseEntity.ok(companySettingsService.saveSettings(dto));
    }
}


package com.app.billing.controller;

import com.app.billing.dto.ChangePasswordRequest;
import com.app.billing.dto.ResetPasswordRequest;
import com.app.billing.service.PasswordManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Password Management", description = "Change and reset passwords")
public class PasswordController {

    private final PasswordManagementService passwordManagementService;

    @PostMapping("/auth/change-password")
    @Operation(summary = "Change own password", description = "Authenticated user changes their own password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordManagementService.changeOwnPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/employees/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset employee password", description = "Admin resets an employee's password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id,
                                                             @Valid @RequestBody ResetPasswordRequest request) {
        passwordManagementService.resetPassword(id, request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}

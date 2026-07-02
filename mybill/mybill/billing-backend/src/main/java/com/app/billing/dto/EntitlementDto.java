package com.app.billing.dto;

import com.app.billing.model.ModulePermission;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries the full permission matrix for one category. {@link ModulePermission}
 * serializes cleanly to/from JSON so it is reused directly here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementDto {
    private String id;

    @NotBlank(message = "categoryId is required")
    private String categoryId;

    private String categoryName;

    @Builder.Default
    private Map<String, ModulePermission> modulePermissions = new HashMap<>();
}

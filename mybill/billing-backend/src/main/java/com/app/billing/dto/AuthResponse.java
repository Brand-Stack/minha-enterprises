package com.app.billing.dto;

import com.app.billing.model.ModulePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String role;
    private String employeeId;
    private String employeeName;
    private String categoryId;
    private String categoryName;

    /** Full effective permission matrix for the logged-in user, keyed by module key. */
    @Builder.Default
    private Map<String, ModulePermission> permissions = new HashMap<>();

    /** True for ADMIN users who implicitly have unrestricted access. */
    @Builder.Default
    private boolean admin = false;
}

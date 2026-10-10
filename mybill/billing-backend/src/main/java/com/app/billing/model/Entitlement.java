package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Permission matrix for a single {@link EmployeeCategory}. There is exactly one
 * entitlement document per category. The {@code modulePermissions} map is keyed by
 * the module key from the {@code module_registry} (e.g. CLIENTS, ACCOUNTING).
 */
@Document(collection = "entitlements")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Entitlement extends BaseEntity {

    @Indexed(unique = true)
    private String categoryId;

    @lombok.Builder.Default
    private Map<String, ModulePermission> modulePermissions = new HashMap<>();
}

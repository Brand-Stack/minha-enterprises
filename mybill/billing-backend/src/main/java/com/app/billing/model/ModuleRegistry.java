package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata describing a single securable module / sub-module in the application.
 *
 * <p>The registry is seeded from a static list at startup so that new modules appear
 * automatically in Entitlement Management. Each registry entry declares which standard
 * operations and which named actions/fields/dashboard-cards are applicable, so the
 * matrix UI can render only relevant columns.</p>
 */
@Document(collection = "module_registry")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ModuleRegistry extends BaseEntity {

    @Indexed(unique = true)
    private String moduleKey;

    private String displayName;

    /** Logical group / parent section (e.g. GENERAL, MASTER, BILLING). Null for top-level. */
    private String parent;

    /** Display ordering within the matrix UI. */
    private int sortOrder;

    /** Standard operations applicable to this module (view, create, edit, delete, export, print, email, download). */
    @lombok.Builder.Default
    private List<String> operations = new ArrayList<>();

    /** Named button/action keys applicable to this module. */
    @lombok.Builder.Default
    private List<ModuleAction> actions = new ArrayList<>();

    /** Optional securable field keys for field-level security. */
    @lombok.Builder.Default
    private List<ModuleAction> fields = new ArrayList<>();

    /** Dashboard card keys for dashboard entitlement control. */
    @lombok.Builder.Default
    private List<ModuleAction> dashboardCards = new ArrayList<>();

    /** Whether this registry entry represents a dashboard module. */
    @lombok.Builder.Default
    private boolean dashboard = false;

    @Data
    @lombok.Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleAction {
        private String key;
        private String displayName;
    }
}

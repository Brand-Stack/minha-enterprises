package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Permission set for a single module / sub-module. Embedded inside {@link Entitlement}.
 *
 * <p>Standard CRUD-style flags cover the common operations, while {@code actions},
 * {@code fields} and {@code dashboardCards} support button-level, field-level and
 * dashboard-card-level granularity respectively.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModulePermission {

    @Builder.Default
    private boolean view = false;
    @Builder.Default
    private boolean create = false;
    @Builder.Default
    private boolean edit = false;
    @Builder.Default
    private boolean delete = false;
    @Builder.Default
    private boolean export = false;
    @Builder.Default
    private boolean print = false;
    @Builder.Default
    private boolean email = false;
    @Builder.Default
    private boolean download = false;

    /** Button / action-level permissions keyed by action key (e.g. "CLEAR_PENDING_AWBS"). */
    @Builder.Default
    private Map<String, Boolean> actions = new HashMap<>();

    /** Optional field-level security keyed by field key (e.g. "amount", "profit"). */
    @Builder.Default
    private Map<String, FieldPermission> fields = new HashMap<>();

    /** Dashboard card visibility keyed by card key (e.g. "VIEW_REVENUE"). */
    @Builder.Default
    private Map<String, Boolean> dashboardCards = new HashMap<>();

    /**
     * Resolve a standard CRUD-style action flag by its key. Falls back to the
     * {@code actions} map for non-standard / button-level actions.
     *
     * @param action case-insensitive action key
     * @return true if the action is permitted
     */
    public boolean isAllowed(String action) {
        if (action == null) {
            return false;
        }
        switch (action.toLowerCase()) {
            case "view":
                return view;
            case "create":
                return create;
            case "edit":
                return edit;
            case "delete":
                return delete;
            case "export":
                return export;
            case "print":
                return print;
            case "email":
                return email;
            case "download":
                return download;
            default:
                // Button / action-level lookup (case-sensitive key match first, then upper-case)
                Boolean explicit = actions.get(action);
                if (explicit != null) {
                    return explicit;
                }
                Boolean upper = actions.get(action.toUpperCase());
                return upper != null && upper;
        }
    }
}

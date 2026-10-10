package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional field-level security for a single field within a module
 * (e.g. Amount, Revenue, GST, Profit).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldPermission {
    @Builder.Default
    private boolean visible = false;
    @Builder.Default
    private boolean editable = false;
}

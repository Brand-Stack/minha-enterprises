package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer-defined employee category / role (e.g. Accountant, Delivery Boy, Operations).
 * Each category is linked to an {@link Entitlement} document that holds its permission matrix.
 */
@Document(collection = "employee_categories")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeCategory extends BaseEntity {

    private String name;
    private String description;
    private Status status;

    public enum Status {
        ACTIVE, INACTIVE
    }
}

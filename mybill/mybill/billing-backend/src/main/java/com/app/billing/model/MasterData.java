package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "master_data")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MasterData extends BaseEntity {
    private String name;
    private String description;
    private MasterDataType type;
    private Boolean active;
    private String role; // Super Admin, Manager, General Staff, Supervisor
    
    public enum MasterDataType {
        ITEM_CATEGORY,
        ITEM_UNIT,
        EMPLOYEE_CATEGORY,
        EXPENSE_CATEGORY
    }
}


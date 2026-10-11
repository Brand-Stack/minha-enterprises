package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "items")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Item extends BaseEntity {
    private String itemCode;
    private String enCode; // EN Code for item identification
    private String itemName;
    private String description;
    private String category; // Must be from Master (ITEM_CATEGORY)
    private String unit; // Must be from Master (ITEM_UNIT)
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private BigDecimal stockQuantity;
    private BigDecimal minStockLevel;
    private String hsnCode;
    private BigDecimal taxRate;
    private java.util.Map<String, Object> customFields; // Custom fields support
}


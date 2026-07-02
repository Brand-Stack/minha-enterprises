package com.app.billing.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "zone_configurations")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ZoneConfiguration extends BaseEntity {

    private String zoneName; // e.g., "LOCAL within CHENNAI"
    private String zoneType; // e.g., "EXPRESS_SURFACE" or "PRIORITY_SAFETY"

    // Slab Definitions for Express / Priority
    private Double expressBaseWeight; // e.g., 0.25 (250g)
    private Double expressIncrementalWeight; // e.g., 0.50 (500g)
    private Double expressPerKgThreshold; // e.g., 3.0 (above 3kg per kg)

    // Slab Definitions for Surface / Safety Plus
    private Double surfaceSlab1Threshold; // e.g., 10.0 (above 10kg)
    private Double surfaceSlab1Max; // e.g., 200.0 (upto 200kg)
    private Double surfaceSlab2Threshold; // e.g., 200.0 (above 200kg)
    private Double surfaceSlab2Max; // e.g., 500.0 (upto 500kg)

    private Boolean isActive;

    @lombok.Builder.Default
    private Boolean isDeleted = false;
}

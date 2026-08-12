package com.app.billing.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@NoArgsConstructor
public class ZoneConfigurationDto {

    private String id;

    @NotBlank(message = "Zone Name is required")
    private String zoneName;

    @NotBlank(message = "Zone Type is required")
    private String zoneType;

    @NotNull(message = "Express Base Weight is required")
    private Double expressBaseWeight;

    @NotNull(message = "Express Incremental Weight is required")
    private Double expressIncrementalWeight;

    @NotNull(message = "Express Per Kg Threshold is required")
    private Double expressPerKgThreshold;

    @NotNull(message = "Surface Slab 1 Threshold is required")
    private Double surfaceSlab1Threshold;

    @NotNull(message = "Surface Slab 1 Max is required")
    private Double surfaceSlab1Max;

    @NotNull(message = "Surface Slab 2 Threshold is required")
    private Double surfaceSlab2Threshold;

    @NotNull(message = "Surface Slab 2 Max is required")
    private Double surfaceSlab2Max;

    private Boolean isActive;
    private String lastUpdatedBy;
}

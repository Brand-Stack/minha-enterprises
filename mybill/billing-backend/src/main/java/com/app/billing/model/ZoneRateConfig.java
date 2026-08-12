package com.app.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneRateConfig {

    // Links to the dynamic ZoneConfiguration ID or key
    private String zoneId;
    private String zoneName;

    // Express / Priority Values
    private Double expressBaseRate;
    private Double expressIncrementalRate;
    private Double expressPerKgRate;

    // Surface / Safety Plus Values
    private Double surfaceSlab1Rate;
    private Double surfaceSlab2Rate;

    /** Standard mode: base rate for 3 kg (legacy). */
    private Double standardBaseRate3Kg;
    /** Standard mode: additional rate per kg above 3 kg (legacy). */
    private Double standardAdditionalPerKg;

    /** Standard mode: 1 kg slab rate. */
    private Double standardRate1Kg;
    /** Standard mode: 2 kg slab rate. */
    private Double standardRate2Kg;
    /** Standard mode: 3 kg slab rate. */
    private Double standardRate3Kg;
    /** Standard mode: per kg rate above 3 kg. */
    private Double standardPerKgAbove3;

    /** Standard mode: 4 kg slab rate. */
    private Double standardRate4Kg;
    /** Standard mode: 5 kg slab rate. */
    private Double standardRate5Kg;
}

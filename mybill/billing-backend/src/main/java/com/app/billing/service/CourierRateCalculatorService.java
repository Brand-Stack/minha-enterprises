package com.app.billing.service;

import com.app.billing.dao.ZoneConfigurationRepository;
import com.app.billing.model.ZoneConfiguration;
import com.app.billing.model.ZoneRateConfig;
import com.app.billing.util.CourierWeightSlabUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rate calculator for courier quotations. Supports:
 * <ul>
 *   <li>EXPRESS_RATE: Up to 3 kg = split slabs (250g, then 250–500 as one 500-rate step, then 500g steps); above 3 kg = ceil(weight) × per-kg rate</li>
 *   <li>SURFACE_RATE: Min weight 10 kg; 0–200 kg = slab1 per kg, above 200 kg = slab2 per kg</li>
 *   <li>PriorityClass (Safety Priority): same split slab model as Express to 3 kg, then ceil kg × per kg</li>
 *   <li>SafetyPlus (Surface on P-zones): same as Surface Rate (min 10 kg, slab1/slab2 per kg)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierRateCalculatorService {

    private final ZoneConfigurationRepository zoneConfigRepository;

    private static final double EXPRESS_UP_TO_KG = 3.0;
    private static final double SURFACE_MIN_KG = 10.0;
    private static final double SURFACE_SLAB2_THRESHOLD_KG = 200.0;

    /**
     * Calculates the amount based on quotation rate type and zone configuration.
     *
     * @param zoneId    Zone configuration ID (required)
     * @param rateType  EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass (from quotation)
     * @param weight    Weight in Kg (required, must be &gt; 0)
     * @param zoneRates List of zone rate configurations from active quotation
     * @return Calculated amount, or 0.0 if data is incomplete/invalid
     */
    public Double calculateAmount(String zoneId, String rateType, Double weight, List<ZoneRateConfig> zoneRates) {
        if (zoneId == null || zoneId.isBlank()) {
            log.warn("Zone is required for calculation");
            return 0.0;
        }
        if (weight == null || weight <= 0) {
            log.warn("Weight must be positive for calculation");
            return 0.0;
        }
        if (zoneRates == null || zoneRates.isEmpty()) {
            log.warn("Zone rates configuration is empty");
            return 0.0;
        }

        ZoneRateConfig rateConfig = zoneRates.stream()
                .filter(zr -> zoneId.equals(zr.getZoneId()))
                .findFirst()
                .orElse(null);

        if (rateConfig == null) {
            log.warn("Rate configuration not found for Zone ID: {}", zoneId);
            return 0.0;
        }

        ZoneConfiguration zoneConfig = zoneConfigRepository.findById(zoneId).orElse(null);
        if (zoneConfig == null || Boolean.FALSE.equals(zoneConfig.getIsActive())) {
            log.warn("Zone configuration not found or inactive for Zone ID: {}", zoneId);
            return 0.0;
        }

        double totalAmount;
        if ("STANDARD".equalsIgnoreCase(zoneConfig.getZoneType())) {
            totalAmount = calculateStandard(weight, rateConfig);
        } else if (com.app.billing.model.CourierQuotation.RATE_TYPE_EXPRESS.equals(rateType)) {
            totalAmount = calculateExpress(weight, zoneConfig, rateConfig);
        } else if (com.app.billing.model.CourierQuotation.RATE_TYPE_SURFACE.equals(rateType)) {
            totalAmount = calculateSurface(weight, zoneConfig, rateConfig);
        } else if (com.app.billing.model.CourierQuotation.RATE_TYPE_SAFETY_PLUS.equals(rateType)) {
            totalAmount = calculateSurface(weight, zoneConfig, rateConfig);
        } else {
            totalAmount = calculateExpress(weight, zoneConfig, rateConfig);
        }
        return round(totalAmount);
    }

    /**
     * Standard mode: rounded-up integer weight used for calculation only.
     * Slabs: 1 kg rate, 2 kg rate, 3 kg rate; above 3 kg = 3 kg rate + (roundedKg - 3) × per kg rate.
     * Falls back to legacy 2-field logic if new 4 slab rates are not configured.
     */
    private double calculateStandard(double weightKg, ZoneRateConfig rateConfig) {
        int roundedKg = (int) Math.ceil(weightKg <= 0 ? 1 : weightKg);
        double rate1 = nullSafe(rateConfig.getStandardRate1Kg(), 0.0);
        double rate2 = nullSafe(rateConfig.getStandardRate2Kg(), 0.0);
        double rate3 = nullSafe(rateConfig.getStandardRate3Kg(), 0.0);
        double rate4 = nullSafe(rateConfig.getStandardRate4Kg(), 0.0);
        double rate5 = nullSafe(rateConfig.getStandardRate5Kg(), 0.0);
        double perKgAbove3 = nullSafe(rateConfig.getStandardPerKgAbove3(), 0.0);
        boolean useNewSlabs = (rate1 > 0 || rate2 > 0 || rate3 > 0 || rate4 > 0 || rate5 > 0 || perKgAbove3 > 0);

        if (useNewSlabs) {
            if (roundedKg == 1) return round(rate1);
            if (roundedKg == 2) return round(rate2);
            if (roundedKg == 3) return round(rate3);
            if (roundedKg == 4 && rate4 > 0) return round(rate4);
            if (roundedKg == 5 && rate5 > 0) return round(rate5);
            
            // If rate4 is not provided, legacy rate3 continues. 
            // If rate5 is not provided, use the last valid rate + increments.
            // But per request "No impact to Above 3 Kg -> Per Kg logic", 
            // we apply the slab values directly. "Use 4 Kg slab rate when weight = 4 Kg".
            // If weight > 5 Kg, base it off rate5. Or if weight = 4 but rate4=0, it falls back to 4kg * perKgAbove3?
            // "Existing slabs must work exactly the same."
            // Existing logic: rate3 + (roundedKg - 3) * perKgAbove3.
            // Requirement: "System supports: 1kg, 2kg, 3kg, 4kg, 5kg, Above 5kg"
            // Wait, if it supports Above 5kg, the equation should be rate5 + (roundedKg - 5) * perKgAbove3 if rate5 is present.
            // "Per Kg continues as per existing logic" could mean we still add `perKgAbove` incrementally.
            if (roundedKg > 5 && rate5 > 0) {
                return round(rate5 + (roundedKg - 5) * perKgAbove3);
            }
            if (roundedKg > 3) {
                // If it falls here (e.g. 4kg but no rate4, or above 3 but no rate5), use legacy above 3
                return round(rate3 + (roundedKg - 3) * perKgAbove3);
            }
        }
        double baseRate = nullSafe(rateConfig.getStandardBaseRate3Kg(), 0.0);
        double additionalPerKg = nullSafe(rateConfig.getStandardAdditionalPerKg(), 0.0);
        if (baseRate <= 0) return 0.0;
        int billableKg = Math.max(roundedKg, 3);
        if (billableKg <= 3) return round(baseRate);
        return round(baseRate + (billableKg - 3) * additionalPerKg);
    }

    /**
     * Express / Safety(Priority): up to threshold = split slabs via {@code CourierWeightSlabUtil.calculateBelow3Kg}
     * (250g + 250–500 + 500g steps); above threshold = {@code ceil(weightKg) × perKgRate}.
     */
    private double calculateExpress(double weightKg, ZoneConfiguration zoneConfig, ZoneRateConfig rateConfig) {
        double baseWeight = nullSafe(zoneConfig.getExpressBaseWeight(), 0.250);
        double incWeight = nullSafe(zoneConfig.getExpressIncrementalWeight(), 0.500);
        double slab2Max = nullSafe(zoneConfig.getExpressPerKgThreshold(), EXPRESS_UP_TO_KG);
        double baseRate = nullSafe(rateConfig.getExpressBaseRate(), 0.0);
        double incRate = nullSafe(rateConfig.getExpressIncrementalRate(), 0.0);
        double perKgRate = nullSafe(rateConfig.getExpressPerKgRate(), 0.0);
        return round(CourierWeightSlabUtil.calculateExpressStyleAmount(weightKg, baseWeight, incWeight, slab2Max,
                baseRate, incRate, perKgRate));
    }

    /** Delegates to shared slab util (Express / PriorityClass cumulative band only). */
    private double calculateCumulativeUpTo3Kg(double weightKg, ZoneConfiguration zoneConfig, ZoneRateConfig rateConfig) {
        double baseWeight = nullSafe(zoneConfig.getExpressBaseWeight(), 0.250);
        double incWeight = nullSafe(zoneConfig.getExpressIncrementalWeight(), 0.500);
        double slab2Max = nullSafe(zoneConfig.getExpressPerKgThreshold(), EXPRESS_UP_TO_KG);
        double baseRate = nullSafe(rateConfig.getExpressBaseRate(), 0.0);
        double incRate = nullSafe(rateConfig.getExpressIncrementalRate(), 0.0);
        return CourierWeightSlabUtil.calculateExpressStyleAmount(weightKg, baseWeight, incWeight, slab2Max, baseRate,
                incRate, 0.0);
    }

    /** Surface: min weight 10 kg; weight rounded up to next integer for calculation only. 0–200 kg = slab1 per kg, above 200 = slab2 per kg */
    private double calculateSurface(double weightKg, ZoneConfiguration zoneConfig, ZoneRateConfig rateConfig) {
        double billableKg = Math.max(weightKg, SURFACE_MIN_KG);
        int roundedKg = (int) Math.ceil(billableKg);
        double slab1Max = nullSafe(zoneConfig.getSurfaceSlab1Max(), SURFACE_SLAB2_THRESHOLD_KG);
        double slab1Rate = nullSafe(rateConfig.getSurfaceSlab1Rate(), 0.0);
        double slab2Rate = nullSafe(rateConfig.getSurfaceSlab2Rate(), 0.0);

        if (roundedKg <= slab1Max) {
            return round(roundedKg * slab1Rate);
        }
        return round(roundedKg * slab2Rate);
    }

    /**
     * Applies cumulative slab calculation. Each slab consumes its applicable weight range
     * sequentially; remaining weight flows to the next slab.
     */
    private double calculateCumulativeAmount(double weightKg, ZoneConfiguration zoneConfig, ZoneRateConfig rateConfig) {
        double total = 0.0;

        // Thresholds from configuration (no hardcoding)
        double baseWeight = nullSafe(zoneConfig.getExpressBaseWeight(), 0.250);
        double incWeight = nullSafe(zoneConfig.getExpressIncrementalWeight(), 0.500);
        double slab2Max = nullSafe(zoneConfig.getExpressPerKgThreshold(), 3.0);
        double slab3Max = nullSafe(zoneConfig.getSurfaceSlab1Threshold(), 10.0);
        double slab4Max = nullSafe(zoneConfig.getSurfaceSlab1Max(), 200.0);
        double slab5Max = nullSafe(zoneConfig.getSurfaceSlab2Max(), 500.0);

        // Rates from configuration
        double baseRate = nullSafe(rateConfig.getExpressBaseRate(), 0.0);
        double incRate = nullSafe(rateConfig.getExpressIncrementalRate(), 0.0);
        double slab3Rate = nullSafe(rateConfig.getExpressPerKgRate(), 0.0);
        double slab4Rate = nullSafe(rateConfig.getSurfaceSlab1Rate(), 0.0);
        double slab5Rate = nullSafe(rateConfig.getSurfaceSlab2Rate(), 0.0);

        if (baseRate <= 0) {
            return 0.0;
        }

        // --- Slab 1: First 250 Gms (fixed) ---
        total += baseRate;
        if (weightKg <= baseWeight) {
            return total;
        }

        double remaining = weightKg - baseWeight;

        // --- Slab 2: Every 500 Gms up to 3 Kg ---
        double slab2Range = slab2Max - baseWeight; // e.g. 2.75 kg
        double weightInSlab2 = Math.min(remaining, slab2Range);
        if (weightInSlab2 > 0 && incRate > 0 && incWeight > 0) {
            int slabs = (int) Math.ceil(weightInSlab2 / incWeight);
            total += slabs * incRate;
        }
        remaining -= weightInSlab2;
        if (remaining <= 0) {
            return total;
        }

        // --- Slab 3: Above 3 Kg up to 10 Kg (per Kg) ---
        double slab3Range = slab3Max - slab2Max; // e.g. 7 kg
        double weightInSlab3 = Math.min(remaining, slab3Range);
        if (weightInSlab3 > 0 && slab3Rate > 0) {
            int kgs = (int) Math.ceil(weightInSlab3);
            total += kgs * slab3Rate;
        }
        remaining -= weightInSlab3;
        if (remaining <= 0) {
            return total;
        }

        // --- Slab 4: Above 10 Kg up to 200 Kg (per Kg) ---
        double slab4Range = slab4Max - slab3Max; // e.g. 190 kg
        double weightInSlab4 = Math.min(remaining, slab4Range);
        if (weightInSlab4 > 0 && slab4Rate > 0) {
            int kgs = (int) Math.ceil(weightInSlab4);
            total += kgs * slab4Rate;
        }
        remaining -= weightInSlab4;
        if (remaining <= 0) {
            return total;
        }

        // --- Slab 5: Above 200 Kg up to 500 Kg (per Kg) ---
        double slab5Range = slab5Max - slab4Max; // e.g. 300 kg
        double weightInSlab5 = Math.min(remaining, slab5Range);
        if (weightInSlab5 > 0 && slab5Rate > 0) {
            int kgs = (int) Math.ceil(weightInSlab5);
            total += kgs * slab5Rate;
        }

        return total;
    }

    private double nullSafe(Double value, double defaultValue) {
        return value != null ? value : defaultValue;
    }

    private Double round(Double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}

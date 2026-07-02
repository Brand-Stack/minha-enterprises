package com.app.billing.util;

/**
 * Reusable weight / slab helpers for Express and Safety(Priority) rate types:
 * first 250g slab, then step split (250–500 as one 500-rate slab, then 500g steps) up to the
 * capped weight, then whole-kg ceil × per-kg rate above the threshold.
 */
public final class CourierWeightSlabUtil {

    private static final double GRAMS_PER_KG = 1000.0;
    private static final double GRAM_CEIL_EPS = 1e-9;

    private CourierWeightSlabUtil() {
    }

    /**
     * Amount for weight up to the express/priority threshold using the business split (not ceil(weight/500)):
     * first 250g → {@code first250Rate}; remainder → one 250g step (250–500 band) at {@code every500Rate},
     * then 500g steps until remainder is exhausted (each partial step still pays one full {@code every500Rate}).
     * <p>
     * Weight is rounded up to whole grams before splitting. {@code weightKg} should already be capped
     * at the zone threshold (e.g. 3 kg) by the caller when needed.
     */
    public static double calculateBelow3Kg(double weightKg, double first250Rate, double every500Rate) {
        if (first250Rate <= 0) {
            return 0.0;
        }
        long grams = billableGrams(weightKg);
        if (grams <= 0) {
            return 0.0;
        }
        if (grams <= FIRST_SLAB_GRAMS) {
            return first250Rate;
        }
        double total = first250Rate;
        long remaining = grams - FIRST_SLAB_GRAMS;
        if (remaining > 0) {
            total += every500Rate;
            remaining -= SECOND_SEGMENT_GRAMS;
        }
        while (remaining > 0) {
            total += every500Rate;
            remaining -= FULL_INCREMENT_GRAMS;
        }
        return total;
    }

    private static final int FIRST_SLAB_GRAMS = 250;
    private static final int SECOND_SEGMENT_GRAMS = 250;
    private static final int FULL_INCREMENT_GRAMS = 500;

    private static long billableGrams(double weightKg) {
        if (weightKg <= 0) {
            return 0;
        }
        return (long) Math.ceil(weightKg * GRAMS_PER_KG - GRAM_CEIL_EPS);
    }

    /**
     * Number of incremental slabs (e.g. 500g each) for the weight strictly above the base slab
     * (e.g. 250g), capped so total weight does not exceed {@code slab2MaxKg}.
     * <p>
     * Example: weight 2.0 kg, base 0.25 kg, inc 0.5 kg, max 3 kg → remaining in band = 1.75 →
     * ceil(1.75/0.5) = 4 slabs.
     */
    public static int countIncrementalSlabs(double weightKg, double baseWeightKg, double incWeightKg,
            double slab2MaxKg) {
        if (weightKg <= baseWeightKg || incWeightKg <= 0) {
            return 0;
        }
        double bandEnd = Math.max(baseWeightKg, slab2MaxKg);
        double remainingInBand = Math.min(weightKg - baseWeightKg, bandEnd - baseWeightKg);
        if (remainingInBand <= 0) {
            return 0;
        }
        return (int) Math.ceil(remainingInBand / incWeightKg);
    }

    /** Whole kilograms to bill when weight is strictly above the 3 kg slab (Math.ceil of kg). */
    public static int billableWholeKgAboveThreshold(double weightKg, double thresholdKg) {
        if (weightKg <= thresholdKg) {
            return 0;
        }
        return (int) Math.ceil(weightKg);
    }

    /**
     * Express / Safety(Priority) amount: {@link #calculateBelow3Kg(double, double, double)} for weight up to
     * {@code slab2MaxKg} (geometry fixed at 250g + 250–500 + 500g steps; rates from {@code baseRate} /
     * {@code incRate}), or {@code ceil(weightKg) × perKgRate} when strictly above {@code slab2MaxKg}.
     * <p>
     * {@code baseWeightKg} and {@code incWeightKg} are retained for API compatibility; slab geometry follows
     * business rules (250g / 500g split), not those parameters.
     */
    public static double calculateExpressStyleAmount(double weightKg, double baseWeightKg, double incWeightKg,
            double slab2MaxKg, double baseRate, double incRate, double perKgRate) {
        if (baseRate <= 0) {
            return 0.0;
        }
        if (weightKg <= slab2MaxKg) {
            double cappedKg = Math.min(weightKg, slab2MaxKg);
            return calculateBelow3Kg(cappedKg, baseRate, incRate);
        }
        if (perKgRate <= 0) {
            return 0.0;
        }
        int kgs = billableWholeKgAboveThreshold(weightKg, slab2MaxKg);
        // For weight &gt; 3 kg, requirement: ceil(weight) × per-kg (e.g. 3.01 → 4 × rate)
        return kgs * perKgRate;
    }
}

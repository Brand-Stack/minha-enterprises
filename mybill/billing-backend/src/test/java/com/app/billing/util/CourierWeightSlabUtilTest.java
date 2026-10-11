package com.app.billing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierWeightSlabUtilTest {

    private static final double R1 = 100.0;
    private static final double R2 = 50.0;

    @Test
    void calculateBelow3Kg_200g_firstSlabOnly() {
        assertEquals(R1, CourierWeightSlabUtil.calculateBelow3Kg(0.2, R1, R2), 1e-9);
    }

    @Test
    void calculateBelow3Kg_300g_firstPlusOne500Slab() {
        // 50g remainder after 250 → one 250–500 band charged at every500 rate
        assertEquals(R1 + R2, CourierWeightSlabUtil.calculateBelow3Kg(0.3, R1, R2), 1e-9);
    }

    @Test
    void calculateBelow3Kg_500g_twoSlabs() {
        assertEquals(R1 + R2, CourierWeightSlabUtil.calculateBelow3Kg(0.5, R1, R2), 1e-9);
    }

    @Test
    void calculateBelow3Kg_750g_firstPlusTwo500Rates() {
        assertEquals(R1 + 2 * R2, CourierWeightSlabUtil.calculateBelow3Kg(0.75, R1, R2), 1e-9);
    }

    @Test
    void calculateBelow3Kg_1200g_firstPlusThree500Rates() {
        assertEquals(R1 + 3 * R2, CourierWeightSlabUtil.calculateBelow3Kg(1.2, R1, R2), 1e-9);
    }

    @Test
    void calculateBelow3Kg_2999g_firstPlusSix500Rates() {
        assertEquals(R1 + 6 * R2, CourierWeightSlabUtil.calculateBelow3Kg(2.999, R1, R2), 1e-9);
    }

    @Test
    void countIncrementalSlabs_twoKg_fourSlabsOf500g() {
        // 2 kg total, 0.25 base → 1.75 kg in band, 0.5 inc → ceil(3.5) = 4
        assertEquals(4, CourierWeightSlabUtil.countIncrementalSlabs(2.0, 0.25, 0.5, 3.0));
    }

    @Test
    void countIncrementalSlabs_edge251g_oneSlab() {
        assertEquals(1, CourierWeightSlabUtil.countIncrementalSlabs(0.251, 0.25, 0.5, 3.0));
    }

    @Test
    void billableWholeKgAboveThreshold_301_ceil4() {
        assertEquals(4, CourierWeightSlabUtil.billableWholeKgAboveThreshold(3.01, 3.0));
    }

    @Test
    void calculateExpressStyle_under250g_firstSlabOnly() {
        double amt = CourierWeightSlabUtil.calculateExpressStyleAmount(0.1, 0.25, 0.5, 3.0, 100, 50, 200);
        assertEquals(100.0, amt, 1e-9);
    }

    @Test
    void calculateExpressStyle_twoKg_firstPlusFourIncrements() {
        // base 100 + 4 * 50 = 300
        double amt = CourierWeightSlabUtil.calculateExpressStyleAmount(2.0, 0.25, 0.5, 3.0, 100, 50, 200);
        assertEquals(300.0, amt, 1e-9);
    }

    @Test
    void calculateExpressStyle_above3kg_ceilTimesPerKg() {
        // 3.01 → 4 * 200 = 800
        double amt = CourierWeightSlabUtil.calculateExpressStyleAmount(3.01, 0.25, 0.5, 3.0, 100, 50, 200);
        assertEquals(800.0, amt, 1e-9);
    }
}

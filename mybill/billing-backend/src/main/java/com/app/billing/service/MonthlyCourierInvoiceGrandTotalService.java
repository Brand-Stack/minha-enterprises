package com.app.billing.service;

import com.app.billing.dao.CourierQuotationRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.MonthlyCourierEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Single source of truth for client-entry invoice totals: per-line base + optional FUEL, FOV, GST.
 * Client-entry quotation overrides take priority over courier quotation / company defaults.
 */
@Service
@RequiredArgsConstructor
public class MonthlyCourierInvoiceGrandTotalService {

    private static final double DEFAULT_PCT = 0.0;

    private final CourierQuotationRepository courierQuotationRepository;
    private final CompanySettingsService companySettingsService;

    public record InvoiceCalculationContext(
            String customerId,
            Double fuelChargePercentageOverride,
            Double fovChargesOverride,
            Double gstPercentageOverride,
            Boolean includeFuel,
            Boolean includeGst,
            Boolean includeFov,
            String discountType,
            Double discountValue,
            Double additionalCharges) {

        public static InvoiceCalculationContext defaults(String customerId) {
            return new InvoiceCalculationContext(customerId, null, null, null, true, true, true, null, null, null);
        }

        public static InvoiceCalculationContext fromQuotation(
                String customerId,
                Double fuelOverride,
                Double fovOverride,
                Double gstOverride,
                Boolean includeFuel,
                Boolean includeGst,
                Boolean includeFov) {
            return fromQuotation(customerId, fuelOverride, fovOverride, gstOverride, includeFuel, includeGst, includeFov, null, null, null);
        }

        public static InvoiceCalculationContext fromQuotation(
                String customerId,
                Double fuelOverride,
                Double fovOverride,
                Double gstOverride,
                Boolean includeFuel,
                Boolean includeGst,
                Boolean includeFov,
                String discountType,
                Double discountValue,
                Double additionalCharges) {
            return new InvoiceCalculationContext(
                    customerId,
                    fuelOverride,
                    fovOverride,
                    gstOverride,
                    includeFuel != null ? includeFuel : true,
                    includeGst != null ? includeGst : true,
                    includeFov != null ? includeFov : true,
                    discountType,
                    discountValue,
                    additionalCharges);
        }
    }

    /** PDF/invoice display breakdown aligned with calculation engine. */
    public record PdfInvoiceTotals(
            double baseAmount,
            double fuelPct,
            double fuelAmount,
            double fovPct,
            double fovAmount,
            double subTotal,
            double discountAmount,
            double additionalCharges,
            double taxableAmount,
            double gstPct,
            double cgst,
            double sgst,
            double roundOff,
            double nettAmount) {}

    public double computeInvoiceGrandTotal(String customerId, List<MonthlyCourierEntry> entries) {
        return computeInvoiceGrandTotal(InvoiceCalculationContext.defaults(customerId), entries);
    }

    public double computeInvoiceGrandTotal(InvoiceCalculationContext ctx, List<MonthlyCourierEntry> entries) {
        return computeInvoiceBreakdown(ctx, entries).totalRevenue().doubleValue();
    }

    public InvoiceRevenueBreakdown computeInvoiceBreakdown(String customerId, List<MonthlyCourierEntry> entries) {
        return computeInvoiceBreakdown(InvoiceCalculationContext.defaults(customerId), entries);
    }

    public InvoiceRevenueBreakdown computeInvoiceBreakdown(InvoiceCalculationContext ctx, List<MonthlyCourierEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new InvoiceRevenueBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        double fuelPct = resolveFuelPercentage(ctx);
        double fovPct = resolveFovPercentage(ctx);
        double gstPct = resolveGstPercentage(ctx);
        boolean applyFuel = ctx.includeFuel() == null || Boolean.TRUE.equals(ctx.includeFuel());
        boolean applyGst = ctx.includeGst() == null || Boolean.TRUE.equals(ctx.includeGst());
        boolean applyFov = ctx.includeFov() == null || Boolean.TRUE.equals(ctx.includeFov());

        BigDecimal baseTotal = BigDecimal.ZERO;
        BigDecimal fuelTotal = BigDecimal.ZERO;
        BigDecimal fovTotal = BigDecimal.ZERO;
        BigDecimal gstEligibleSubTotal = BigDecimal.ZERO;
        for (MonthlyCourierEntry e : entries) {
            double base = lineBase(e);
            double fuel = applyFuel && chargeApplies(e.getFuelApplicable()) && fuelPct > 0 ? base * (fuelPct / 100.0) : 0.0;
            double fov = applyFov && chargeApplies(e.getFovApplicable()) && fovPct > 0 ? base * (fovPct / 100.0) : 0.0;
            double lineSub = base + fuel + fov;
            baseTotal = baseTotal.add(BigDecimal.valueOf(base));
            fuelTotal = fuelTotal.add(BigDecimal.valueOf(fuel));
            fovTotal = fovTotal.add(BigDecimal.valueOf(fov));
            if (chargeApplies(e.getGstApplicable())) {
                gstEligibleSubTotal = gstEligibleSubTotal.add(BigDecimal.valueOf(lineSub));
            }
        }

        BigDecimal subTotal = baseTotal.add(fuelTotal).add(fovTotal);

        // Calculate Discount
        BigDecimal discountAmt = BigDecimal.ZERO;
        if (ctx.discountValue() != null && ctx.discountValue() > 0) {
            if ("PERCENTAGE".equalsIgnoreCase(ctx.discountType())) {
                discountAmt = subTotal.multiply(BigDecimal.valueOf(ctx.discountValue() / 100.0));
            } else {
                discountAmt = BigDecimal.valueOf(ctx.discountValue());
            }
        }
        if (discountAmt.compareTo(subTotal) > 0) {
            discountAmt = subTotal;
        }

        // Additional Charges
        BigDecimal addlCharges = BigDecimal.ZERO;
        if (ctx.additionalCharges() != null && ctx.additionalCharges() > 0) {
            addlCharges = BigDecimal.valueOf(ctx.additionalCharges());
        }

        // Taxable Amount = Sub Total - Discount + Additional Charges
        BigDecimal taxableAmount = subTotal.subtract(discountAmt).add(addlCharges);
        if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            taxableAmount = BigDecimal.ZERO;
        }

        // GST is strictly calculated on Taxable Amount (pro-rated for lines where GST applies)
        BigDecimal gstTotal = BigDecimal.ZERO;
        if (applyGst && gstPct > 0 && subTotal.compareTo(BigDecimal.ZERO) > 0 && gstEligibleSubTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxableForGst = taxableAmount.multiply(gstEligibleSubTotal)
                    .divide(subTotal, 4, RoundingMode.HALF_UP);
            gstTotal = taxableForGst.multiply(BigDecimal.valueOf(gstPct / 100.0));
        }

        BigDecimal finalTotal = taxableAmount.add(gstTotal);
        return new InvoiceRevenueBreakdown(
                money(baseTotal),
                money(fuelTotal),
                money(fovTotal),
                money(subTotal),
                money(discountAmt),
                money(addlCharges),
                money(taxableAmount),
                money(gstTotal),
                money(finalTotal));
    }

    public PdfInvoiceTotals computePdfTotals(InvoiceCalculationContext ctx, List<MonthlyCourierEntry> entries) {
        InvoiceRevenueBreakdown b = computeInvoiceBreakdown(ctx, entries);
        double base = b.baseRevenue().doubleValue();
        double fuel = b.fuelCharges().doubleValue();
        double fov = b.fovAmount().doubleValue();
        double subTotal = b.subTotal().doubleValue();
        double discount = b.discountAmount().doubleValue();
        double addl = b.additionalCharges().doubleValue();
        double taxable = b.taxableAmount().doubleValue();
        double gst = b.gstAmount().doubleValue();
        double fuelPct = resolveFuelPercentage(ctx);
        double fovPct = resolveFovPercentage(ctx);
        double gstPct = resolveGstPercentage(ctx);
        double cgst = gst / 2.0;
        double sgst = gst / 2.0;
        double grossBeforeRound = taxable + gst;
        double nettAmount = roundToRupee(grossBeforeRound);
        double roundOff = nettAmount - grossBeforeRound;
        return new PdfInvoiceTotals(base, fuelPct, fuel, fovPct, fov, subTotal, discount, addl, taxable, gstPct, cgst, sgst, roundOff, nettAmount);
    }

    /** Validates GST % for client-entry override: 0–100 inclusive, decimals allowed. */
    public static void validateGstPercentage(Double gstPercentage) {
        if (gstPercentage == null || gstPercentage.isNaN()) {
            return;
        }
        if (gstPercentage < 0 || gstPercentage > 100) {
            throw new IllegalArgumentException("GST percentage must be between 0 and 100");
        }
    }

    private static boolean chargeApplies(Boolean nullableFlag) {
        return nullableFlag == null || Boolean.TRUE.equals(nullableFlag);
    }

    private static double lineBase(MonthlyCourierEntry e) {
        double amt = e.getAmount() != null ? e.getAmount() : 0.0;
        double add = e.getAdditionalCharges() != null ? e.getAdditionalCharges() : 0.0;
        return amt + add;
    }

    public LineRevenueBreakdown lineBreakdown(
            MonthlyCourierEntry e,
            double fuelPct,
            double fovPct,
            double gstPct,
            boolean applyFuel,
            boolean applyGst,
            boolean applyFov) {
        double base = lineBase(e);
        double fuel = applyFuel && chargeApplies(e.getFuelApplicable()) && fuelPct > 0
                ? base * (fuelPct / 100.0) : 0.0;
        double fov = applyFov && chargeApplies(e.getFovApplicable()) && fovPct > 0
                ? base * (fovPct / 100.0) : 0.0;
        double sub = base + fuel + fov;
        double gst = applyGst && chargeApplies(e.getGstApplicable()) && gstPct > 0
                ? sub * (gstPct / 100.0) : 0.0;
        return new LineRevenueBreakdown(
                money(BigDecimal.valueOf(base)),
                money(BigDecimal.valueOf(fuel)),
                money(BigDecimal.valueOf(fov)),
                money(BigDecimal.valueOf(gst)));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static double roundToRupee(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public double resolveFuelPercentage(InvoiceCalculationContext ctx) {
        Double override = ctx.fuelChargePercentageOverride();
        if (override != null && !override.isNaN() && override >= 0) {
            return override;
        }
        return resolveConfiguredPercentage(ctx.customerId(), true);
    }

    public double resolveFovPercentage(InvoiceCalculationContext ctx) {
        Double override = ctx.fovChargesOverride();
        if (override != null && !override.isNaN() && override >= 0) {
            return override;
        }
        return resolveConfiguredPercentage(ctx.customerId(), false);
    }

    /**
     * Priority: client-entry GST % → company default GST % → 0%.
     */
    public double resolveGstPercentage(InvoiceCalculationContext ctx) {
        Double override = ctx.gstPercentageOverride();
        if (override != null && !override.isNaN() && override >= 0) {
            return override;
        }
        CompanySettingsDto settings = companySettingsService.getSettings();
        Double companyDefault = settings != null ? settings.getDefaultGstPercentage() : null;
        if (companyDefault != null && !companyDefault.isNaN() && companyDefault >= 0) {
            return companyDefault;
        }
        return 18.0; // Default GST is 18.0% if blank/null
    }

    /** Uses quotation value only when explicitly set and non-negative; otherwise 0. */
    private double resolveConfiguredPercentage(String customerId, boolean fuel) {
        if (customerId == null || customerId.isBlank()) {
            return DEFAULT_PCT;
        }
        List<CourierQuotation> quotes = courierQuotationRepository.findByCustomerId(customerId);
        return quotes.stream()
                .filter(q -> q.getStatus() == CourierQuotation.QuotationStatus.ACTIVE)
                .findFirst()
                .map(q -> fuel ? q.getFuelChargePercentage() : q.getFovCharges())
                .filter(p -> p != null && !p.isNaN() && p >= 0)
                .orElse(DEFAULT_PCT);
    }

    public record InvoiceRevenueBreakdown(
            BigDecimal baseRevenue,
            BigDecimal fuelCharges,
            BigDecimal fovAmount,
            BigDecimal subTotal,
            BigDecimal discountAmount,
            BigDecimal additionalCharges,
            BigDecimal taxableAmount,
            BigDecimal gstAmount,
            BigDecimal totalRevenue) {

        public InvoiceRevenueBreakdown(BigDecimal baseRevenue, BigDecimal fuelCharges, BigDecimal gstAmount, BigDecimal fovAmount, BigDecimal totalRevenue) {
            this(baseRevenue, fuelCharges, fovAmount, baseRevenue.add(fuelCharges).add(fovAmount), BigDecimal.ZERO, BigDecimal.ZERO, baseRevenue.add(fuelCharges).add(fovAmount), gstAmount, totalRevenue);
        }
    }

    public record LineRevenueBreakdown(BigDecimal base, BigDecimal fuel, BigDecimal fov, BigDecimal gst) {}
}

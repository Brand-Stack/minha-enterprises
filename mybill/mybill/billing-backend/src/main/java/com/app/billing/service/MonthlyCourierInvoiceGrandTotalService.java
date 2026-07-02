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
            Boolean includeFov) {

        public static InvoiceCalculationContext defaults(String customerId) {
            return new InvoiceCalculationContext(customerId, null, null, null, true, true, true);
        }

        public static InvoiceCalculationContext fromQuotation(
                String customerId,
                Double fuelOverride,
                Double fovOverride,
                Double gstOverride,
                Boolean includeFuel,
                Boolean includeGst,
                Boolean includeFov) {
            return new InvoiceCalculationContext(
                    customerId,
                    fuelOverride,
                    fovOverride,
                    gstOverride,
                    includeFuel != null ? includeFuel : true,
                    includeGst != null ? includeGst : true,
                    includeFov != null ? includeFov : true);
        }
    }

    /** PDF/invoice display breakdown aligned with per-line calculation engine. */
    public record PdfInvoiceTotals(
            double baseAmount,
            double fuelPct,
            double fuelAmount,
            double fovPct,
            double fovAmount,
            double subTotal,
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
            return new InvoiceRevenueBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
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
        BigDecimal gstTotal = BigDecimal.ZERO;
        for (MonthlyCourierEntry e : entries) {
            LineRevenueBreakdown line = lineBreakdown(e, fuelPct, fovPct, gstPct, applyFuel, applyGst, applyFov);
            baseTotal = baseTotal.add(line.base());
            fuelTotal = fuelTotal.add(line.fuel());
            fovTotal = fovTotal.add(line.fov());
            gstTotal = gstTotal.add(line.gst());
        }
        BigDecimal total = baseTotal.add(fuelTotal).add(fovTotal).add(gstTotal);
        return new InvoiceRevenueBreakdown(
                money(baseTotal),
                money(fuelTotal),
                money(gstTotal),
                money(fovTotal),
                money(total));
    }

    public PdfInvoiceTotals computePdfTotals(InvoiceCalculationContext ctx, List<MonthlyCourierEntry> entries) {
        InvoiceRevenueBreakdown b = computeInvoiceBreakdown(ctx, entries);
        double base = b.baseRevenue().doubleValue();
        double fuel = b.fuelCharges().doubleValue();
        double fov = b.fovAmount().doubleValue();
        double gst = b.gstAmount().doubleValue();
        double fuelPct = resolveFuelPercentage(ctx);
        double fovPct = resolveFovPercentage(ctx);
        double gstPct = resolveGstPercentage(ctx);
        double subTotal = base + fuel + fov;
        double cgst = gst / 2.0;
        double sgst = gst / 2.0;
        double grossBeforeRound = subTotal + gst;
        double nettAmount = roundToRupee(grossBeforeRound);
        double roundOff = nettAmount - grossBeforeRound;
        return new PdfInvoiceTotals(base, fuelPct, fuel, fovPct, fov, subTotal, gstPct, cgst, sgst, roundOff, nettAmount);
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

    private static LineRevenueBreakdown lineBreakdown(
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

    private double resolveFuelPercentage(InvoiceCalculationContext ctx) {
        Double override = ctx.fuelChargePercentageOverride();
        if (override != null && !override.isNaN() && override >= 0) {
            return override;
        }
        return resolveConfiguredPercentage(ctx.customerId(), true);
    }

    private double resolveFovPercentage(InvoiceCalculationContext ctx) {
        Double override = ctx.fovChargesOverride();
        if (override != null && !override.isNaN() && override >= 0) {
            return override;
        }
        return resolveConfiguredPercentage(ctx.customerId(), false);
    }

    /**
     * Priority: client-entry GST % → company default GST % → 0%.
     */
    private double resolveGstPercentage(InvoiceCalculationContext ctx) {
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
            BigDecimal gstAmount,
            BigDecimal fovAmount,
            BigDecimal totalRevenue) {}

    private record LineRevenueBreakdown(BigDecimal base, BigDecimal fuel, BigDecimal fov, BigDecimal gst) {}
}

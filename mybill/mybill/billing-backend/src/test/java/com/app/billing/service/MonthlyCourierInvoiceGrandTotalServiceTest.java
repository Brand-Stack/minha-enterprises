package com.app.billing.service;

import com.app.billing.dao.CourierQuotationRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.MonthlyCourierEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyCourierInvoiceGrandTotalServiceTest {

    @Mock
    private CourierQuotationRepository courierQuotationRepository;

    @Mock
    private CompanySettingsService companySettingsService;

    @InjectMocks
    private MonthlyCourierInvoiceGrandTotalService service;

    @Test
    void singleLine_base100_fuel15_gst12_clientOverride() {
        CourierQuotation q = new CourierQuotation();
        q.setStatus(CourierQuotation.QuotationStatus.ACTIVE);
        q.setFuelChargePercentage(15.0);
        q.setFovCharges(null);
        when(courierQuotationRepository.findByCustomerId("cust-1")).thenReturn(List.of(q));

        MonthlyCourierEntry e = new MonthlyCourierEntry();
        e.setAmount(100.0);
        e.setAdditionalCharges(0.0);

        var ctx = MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                "cust-1", null, null, 12.0, true, true, true);
        double total = service.computeInvoiceGrandTotal(ctx, List.of(e));
        // base 100 + fuel 15 = 115; GST 12% = 13.80; line total 128.80
        assertEquals(128.80, total, 0.001);
    }

    @Test
    void usesCompanyDefaultGstWhenClientEntryBlank() {
        when(companySettingsService.getSettings()).thenReturn(
                CompanySettingsDto.builder().defaultGstPercentage(18.0).build());

        MonthlyCourierEntry e = new MonthlyCourierEntry();
        e.setAmount(1000.0);

        var ctx = MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                "cust-1", 10.0, 5.0, null, true, true, true);
        double total = service.computeInvoiceGrandTotal(ctx, List.of(e));
        // base 1000 + fuel 100 + fov 50 = 1150; GST 18% = 207; total 1357
        assertEquals(1357.00, total, 0.001);
    }

    @Test
    void noGstConfigured_defaultsToEighteen() {
        when(companySettingsService.getSettings()).thenReturn(CompanySettingsDto.builder().build());

        MonthlyCourierEntry e = new MonthlyCourierEntry();
        e.setAmount(100.0);

        double total = service.computeInvoiceGrandTotal("cust-1", List.of(e));
        assertEquals(118.00, total, 0.001);
    }

    @Test
    void gstDisabledOnLine_excludesGst() {
        CourierQuotation q = new CourierQuotation();
        q.setStatus(CourierQuotation.QuotationStatus.ACTIVE);
        q.setFuelChargePercentage(10.0);
        q.setFovCharges(5.0);
        when(courierQuotationRepository.findByCustomerId("cust-1")).thenReturn(List.of(q));

        MonthlyCourierEntry e = new MonthlyCourierEntry();
        e.setAmount(100.0);
        e.setAdditionalCharges(0.0);
        e.setGstApplicable(false);

        var ctx = MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.fromQuotation(
                "cust-1", null, null, 18.0, true, true, true);
        double total = service.computeInvoiceGrandTotal(ctx, List.of(e));
        // base 100 + fuel 10 + fov 5 = 115; no GST
        assertEquals(115.00, total, 0.001);
    }

    @Test
    void validateGstPercentage_rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> MonthlyCourierInvoiceGrandTotalService.validateGstPercentage(101.0));
        assertThrows(IllegalArgumentException.class,
                () -> MonthlyCourierInvoiceGrandTotalService.validateGstPercentage(-1.0));
    }
}

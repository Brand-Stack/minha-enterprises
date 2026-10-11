package com.app.billing.service;

import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dao.MonthlyCourierQuotationRepository;
import com.app.billing.dto.MonthlyCourierQuotationDto;
import com.app.billing.model.MonthlyCourierQuotation;
import com.app.billing.util.AuditUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyCourierQuotationServiceTest {

    @Mock
    private MonthlyCourierQuotationRepository repository;

    @Mock
    private MonthlyCourierEntryRepository entryRepository;

    @Mock
    private InvoiceSequenceService invoiceSequenceService;

    @Mock
    private MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;

    @Mock
    private AuditUtil auditUtil;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MonthlyCourierQuotationService quotationService;

    private MonthlyCourierQuotation quotation;

    @BeforeEach
    void setUp() {
        quotation = new MonthlyCourierQuotation();
        quotation.setId("Q101");
        quotation.setShopId("SHOP1");
        quotation.setMonth("OCTOBER");
        quotation.setYear(2026);
        quotation.setTitle("Test Client - OCTOBER 2026");
        quotation.setInvoiceDate(LocalDate.of(2026, 11, 5));
        quotation.setInvoiceNumber(null);
        quotation.setInvoiceGenerated(false);
    }

    @Test
    void testGenerateInvoicePreservesClientEntryMonthAndYear() {
        when(repository.findById("Q101")).thenReturn(Optional.of(quotation));
        when(invoiceSequenceService.generateNextInvoiceNumber(any(LocalDate.class))).thenReturn("INV-2026-001");
        when(repository.findAll()).thenReturn(Collections.emptyList());
        when(repository.save(any(MonthlyCourierQuotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepository.findByMonthlyQuotationIdOrderByEntryDateAsc("Q101")).thenReturn(Collections.emptyList());
        when(invoiceGrandTotalService.computeInvoiceBreakdown(any(MonthlyCourierInvoiceGrandTotalService.InvoiceCalculationContext.class), any()))
                .thenReturn(new MonthlyCourierInvoiceGrandTotalService.InvoiceRevenueBreakdown(
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));

        MonthlyCourierQuotationDto dto = quotationService.generateInvoice("Q101");

        assertNotNull(dto);
        // Requirement 3: Client Entry Month and Year must NOT change to November!
        assertEquals("OCTOBER", dto.getMonth(), "Month must remain October");
        assertEquals(2026, dto.getYear(), "Year must remain 2026");
        assertEquals(LocalDate.of(2026, 11, 5), dto.getInvoiceDate(), "Invoice Date must be November 5, 2026");
        assertEquals("INV-2026-001", dto.getInvoiceNumber());
        assertTrue(dto.getInvoiceGenerated());
    }
}

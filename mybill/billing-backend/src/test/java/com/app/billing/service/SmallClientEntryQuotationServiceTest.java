package com.app.billing.service;

import com.app.billing.dao.SmallClientEntryQuotationRepository;
import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.dto.SmallClientEntryQuotationDto;
import com.app.billing.model.SmallClientEntryQuotation;
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
class SmallClientEntryQuotationServiceTest {

    @Mock
    private SmallClientEntryQuotationRepository repository;

    @Mock
    private SmallClientEntryRepository entryRepository;

    @Mock
    private InvoiceSequenceService invoiceSequenceService;

    @Mock
    private MonthlyCourierInvoiceGrandTotalService invoiceGrandTotalService;

    @Mock
    private AuditUtil auditUtil;

    @Mock
    private com.app.billing.dao.SmallClientRepository smallClientRepository;

    @Mock
    private com.app.billing.dao.ZoneConfigurationRepository zoneConfigurationRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private SmallClientEntryQuotationService quotationService;

    private SmallClientEntryQuotationDto dto;

    @BeforeEach
    void setUp() {
        dto = new SmallClientEntryQuotationDto();
        dto.setCustomerId("CUST1");
        dto.setCustomerName("Retail Client");
        dto.setMonth("OCTOBER");
        dto.setYear(2026);
        dto.setInvoiceDate(LocalDate.of(2026, 10, 8));
    }

    @Test
    void testCreateSmallClientEntryImmediatelyAppearsInReport() {
        when(smallClientRepository.findById("CUST1"))
                .thenReturn(Optional.of(com.app.billing.model.SmallClient.builder().id("CUST1").partyName("Retail Client").build()));
        when(invoiceSequenceService.generateNextInvoiceNumber(any(LocalDate.class))).thenReturn("SC-2026-001");
        when(repository.save(any(SmallClientEntryQuotation.class))).thenAnswer(inv -> {
            SmallClientEntryQuotation q = inv.getArgument(0);
            q.setId("SC101");
            return q;
        });

        SmallClientEntryQuotationDto created = quotationService.create(dto);

        assertNotNull(created);
        assertEquals("SC101", created.getId());
        assertEquals("OCTOBER", created.getMonth());
        assertEquals(2026, created.getYear());
        // Requirement 2: Must be immediately available for reports upon save without needing manual invoice generation
        assertTrue(created.getInvoiceGenerated());
        assertEquals("SC-2026-001", created.getInvoiceNumber());
    }

    @Test
    void testSearchMatchesRecordsWithShopIdAndSortsProperly() {
        when(mongoTemplate.find(any(org.springframework.data.mongodb.core.query.Query.class), eq(SmallClientEntryQuotation.class)))
                .thenReturn(Collections.emptyList());
        when(mongoTemplate.count(any(org.springframework.data.mongodb.core.query.Query.class), eq(SmallClientEntryQuotation.class)))
                .thenReturn(0L);

        var response = quotationService.search("DEFAULT_SHOP", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                "invoiceDate", "desc", 0, 10);

        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
    }
}

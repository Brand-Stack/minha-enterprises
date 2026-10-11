package com.app.billing.service;

import com.app.billing.model.MonthlyCourierEntry;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MonthlyCourierEntryServiceTest {

    @InjectMocks
    private MonthlyCourierEntryService service;

    @Test
    public void testFilterAndSearchEntries() {
        MonthlyCourierEntry entry1 = MonthlyCourierEntry.builder()
                .trackingNumber("AWB123")
                .courierType("ST")
                .receiverName("ANBU")
                .build();
        MonthlyCourierEntry entry2 = MonthlyCourierEntry.builder()
                .trackingNumber("AWB456")
                .courierType("Express")
                .receiverName("RAMU")
                .build();
        List<MonthlyCourierEntry> list = List.of(entry1, entry2);

        // Scenario 1: Search only
        List<MonthlyCourierEntry> result1 = service.filterAndSearchEntries(list, "st", null);
        assertEquals(1, result1.size());
        assertEquals("AWB123", result1.get(0).getTrackingNumber());

        // Scenario 2: Search By courierType
        List<MonthlyCourierEntry> result2 = service.filterAndSearchEntries(list, "st", "courierType");
        assertEquals(1, result2.size());
        assertEquals("AWB123", result2.get(0).getTrackingNumber());

        // Scenario 3: Search By trackingNumber for "st" (should not match because courierType has "st" but trackingNumber doesn't)
        List<MonthlyCourierEntry> result3 = service.filterAndSearchEntries(list, "st", "trackingNumber");
        assertEquals(0, result3.size());

        // Scenario 4: Search By trackingNumber and courierType together (should match "st" in courierType)
        List<MonthlyCourierEntry> result4 = service.filterAndSearchEntries(list, "st", "trackingNumber,courierType");
        assertEquals(1, result4.size());
    }
}

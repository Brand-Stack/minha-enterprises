package com.app.billing.service;

import com.app.billing.dao.CashBookingRepository;
import com.app.billing.dao.CollectionCenterEntryRepository;
import com.app.billing.dao.CollectionCustomerAwbRepository;
import com.app.billing.dao.MonthlyCourierEntryRepository;
import com.app.billing.dao.SmallClientEntryRepository;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.model.CashBooking;
import com.app.billing.model.CollectionCenterEntry;
import com.app.billing.model.CollectionCustomerAwb;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.util.CourierTrackingNumberValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Ensures AWB / tracking numbers are not duplicated across Monthly Courier (Client Entry),
 * Small Client Entry, Collection Center entries, Cash Booking, and the collection customer
 * AWB registry.
 */
@Service
@RequiredArgsConstructor
public class GlobalAwbUniquenessService {

    private static final String DUP_MSG = "AWB already exists in the system.";

    private final MonthlyCourierEntryRepository monthlyCourierEntryRepository;
    private final SmallClientEntryRepository smallClientEntryRepository;
    private final CollectionCenterEntryRepository collectionCenterEntryRepository;
    private final CollectionCustomerAwbRepository collectionCustomerAwbRepository;
    private final CashBookingRepository cashBookingRepository;

    public void assertAwbAvailableForSmallClientEntry(String normalizedAwb, String excludeEntryId) {
        if (normalizedAwb == null || normalizedAwb.isEmpty() || !CourierTrackingNumberValidator.isPurelyNumeric(normalizedAwb)) {
            return;
        }
        assertNotUsedInMonthly(normalizedAwb, null);
        assertNotUsedInSmallClientEntry(normalizedAwb, excludeEntryId);
        assertNotUsedInCollectionEntry(normalizedAwb, null);
        assertNotUsedInCashBooking(normalizedAwb, null);
        assertRegistryAbsentOrThrow(normalizedAwb, null, null);
    }

    public void assertAwbAvailableForMonthlyEntry(String normalizedAwb, String excludeMonthlyEntryId) {
        if (normalizedAwb == null || normalizedAwb.isEmpty() || !CourierTrackingNumberValidator.isPurelyNumeric(normalizedAwb)) {
            return;
        }
        assertNotUsedInMonthly(normalizedAwb, excludeMonthlyEntryId);
        assertNotUsedInSmallClientEntry(normalizedAwb, null);
        assertNotUsedInCollectionEntry(normalizedAwb, null);
        assertNotUsedInCashBooking(normalizedAwb, null);
        assertRegistryAbsentOrThrow(normalizedAwb, null, null);
    }

    /**
     * New registry row or "new AWB at entry" — AWB must not appear in monthly shipments,
     * collection entries, or the registry.
     */
    public void assertAwbFreeForNewRegistry(String normalizedAwb) {
        if (normalizedAwb == null || normalizedAwb.isEmpty() || !CourierTrackingNumberValidator.isPurelyNumeric(normalizedAwb)) {
            return;
        }
        assertNotUsedInMonthly(normalizedAwb, null);
        assertNotUsedInSmallClientEntry(normalizedAwb, null);
        assertNotUsedInCollectionEntry(normalizedAwb, null);
        assertNotUsedInCashBooking(normalizedAwb, null);
        assertRegistryAbsentOrThrow(normalizedAwb, null, null);
    }

    /**
     * @param consumeRegistryAwbId when saving a collection entry against an existing PENDING registry row,
     *                               pass that registry document id so the same AWB is allowed.
     */
    public void assertAwbAvailableForCollectionCenter(String normalizedAwb, String excludeCollectionCenterId,
            String consumeRegistryAwbId) {
        if (normalizedAwb == null || normalizedAwb.isEmpty() || !CourierTrackingNumberValidator.isPurelyNumeric(normalizedAwb)) {
            return;
        }
        assertNotUsedInMonthly(normalizedAwb, null);
        assertNotUsedInSmallClientEntry(normalizedAwb, null);
        assertNotUsedInCollectionEntry(normalizedAwb, excludeCollectionCenterId);
        assertNotUsedInCashBooking(normalizedAwb, null);
        assertRegistryAbsentOrThrow(normalizedAwb, consumeRegistryAwbId, excludeCollectionCenterId);
    }

    public void assertAwbAvailableForCashBooking(String normalizedAwb, String excludeCashBookingId) {
        if (normalizedAwb == null || normalizedAwb.isEmpty() || !CourierTrackingNumberValidator.isPurelyNumeric(normalizedAwb)) {
            return;
        }
        assertNotUsedInMonthly(normalizedAwb, null);
        assertNotUsedInSmallClientEntry(normalizedAwb, null);
        assertNotUsedInCollectionEntry(normalizedAwb, null);
        assertNotUsedInCashBooking(normalizedAwb, excludeCashBookingId);
        assertRegistryAbsentOrThrow(normalizedAwb, null, null);
    }

    private void assertNotUsedInMonthly(String normalizedAwb, String excludeMonthlyEntryId) {
        Optional<MonthlyCourierEntry> m = monthlyCourierEntryRepository.findFirstByTrackingNumberIgnoreCase(normalizedAwb);
        if (m.isPresent() && (excludeMonthlyEntryId == null || !m.get().getId().equals(excludeMonthlyEntryId))) {
            throw new ResourceAlreadyExistsException(DUP_MSG);
        }
    }

    private void assertNotUsedInSmallClientEntry(String normalizedAwb, String excludeSmallClientEntryId) {
        Optional<SmallClientEntry> s = smallClientEntryRepository.findFirstByTrackingNumberIgnoreCase(normalizedAwb);
        if (s.isPresent() && (excludeSmallClientEntryId == null || !s.get().getId().equals(excludeSmallClientEntryId))) {
            throw new ResourceAlreadyExistsException(DUP_MSG);
        }
    }

    private void assertNotUsedInCollectionEntry(String normalizedAwb, String excludeCollectionCenterId) {
        Optional<CollectionCenterEntry> c = collectionCenterEntryRepository.findFirstByAwbNoIgnoreCase(normalizedAwb);
        if (c.isPresent() && (excludeCollectionCenterId == null || !c.get().getId().equals(excludeCollectionCenterId))) {
            throw new ResourceAlreadyExistsException(DUP_MSG);
        }
    }

    private void assertNotUsedInCashBooking(String normalizedAwb, String excludeCashBookingId) {
        Optional<CashBooking> c = cashBookingRepository.findFirstByAwbNoIgnoreCase(normalizedAwb);
        if (c.isPresent() && (excludeCashBookingId == null || !c.get().getId().equals(excludeCashBookingId))) {
            throw new ResourceAlreadyExistsException(DUP_MSG);
        }
    }

    /**
     * Registry may contain the AWB only if {@code allowRegistryId} matches that row (consuming PENDING slot).
     */
    /**
     * @param allowRegistryId          when consuming a PENDING registry row
     * @param allowUsedCollectionEntryId when updating a collection entry whose USED registry row still references this AWB
     */
    private void assertRegistryAbsentOrThrow(String normalizedAwb, String allowRegistryId, String allowUsedCollectionEntryId) {
        Optional<CollectionCustomerAwb> reg = collectionCustomerAwbRepository.findFirstByAwbNoIgnoreCase(normalizedAwb);
        if (reg.isEmpty()) {
            return;
        }
        CollectionCustomerAwb row = reg.get();
        if (allowRegistryId != null && allowRegistryId.equals(row.getId())
                && CollectionCustomerAwb.STATUS_PENDING.equalsIgnoreCase(String.valueOf(row.getStatus()))) {
            return;
        }
        if (allowUsedCollectionEntryId != null
                && CollectionCustomerAwb.STATUS_USED.equalsIgnoreCase(String.valueOf(row.getStatus()))
                && allowUsedCollectionEntryId.equals(row.getCollectionCenterEntryId())) {
            return;
        }
        throw new ResourceAlreadyExistsException(DUP_MSG);
    }
}

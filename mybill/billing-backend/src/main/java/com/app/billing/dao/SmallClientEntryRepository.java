package com.app.billing.dao;

import com.app.billing.model.SmallClientEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SmallClientEntryRepository extends MongoRepository<SmallClientEntry, String> {

    List<SmallClientEntry> findByMonthlyQuotationIdOrderByEntryDateAsc(String monthlyQuotationId);

    List<SmallClientEntry> findByMonthlyQuotationIdIn(Collection<String> monthlyQuotationIds);

    List<SmallClientEntry> findByEntryDateBetween(LocalDate start, LocalDate end);

    List<SmallClientEntry> findByEntryDate(LocalDate entryDate);

    List<SmallClientEntry> findByEntryDateGreaterThanEqual(LocalDate start);

    List<SmallClientEntry> findByEntryDateLessThanEqual(LocalDate end);

    List<SmallClientEntry> findByCourierTypeIgnoreCase(String courierType);

    Optional<SmallClientEntry> findFirstByTrackingNumberIgnoreCase(String trackingNumber);

    List<SmallClientEntry> findByTrackingNumberContainingIgnoreCase(String trackingNumber);

    List<SmallClientEntry> findByConsigneeAddressContainingIgnoreCase(String fragment);

    List<SmallClientEntry> findByCourierTypeContainingIgnoreCase(String fragment);

    void deleteByMonthlyQuotationId(String monthlyQuotationId);

    boolean existsByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumberAndIdNot(String trackingNumber, String id);
}


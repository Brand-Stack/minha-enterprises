package com.app.billing.dao;

import com.app.billing.model.MonthlyCourierEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyCourierEntryRepository extends MongoRepository<MonthlyCourierEntry, String> {

    List<MonthlyCourierEntry> findByMonthlyQuotationIdOrderByEntryDateAsc(String monthlyQuotationId);
    Page<MonthlyCourierEntry> findByMonthlyQuotationIdOrderByEntryDateAsc(String monthlyQuotationId, Pageable pageable);

    List<MonthlyCourierEntry> findByMonthlyQuotationIdIn(Collection<String> monthlyQuotationIds);

    List<MonthlyCourierEntry> findByEntryDateBetween(LocalDate start, LocalDate end);

    List<MonthlyCourierEntry> findByEntryDate(LocalDate entryDate);

    List<MonthlyCourierEntry> findByEntryDateGreaterThanEqual(LocalDate start);

    List<MonthlyCourierEntry> findByEntryDateLessThanEqual(LocalDate end);

    List<MonthlyCourierEntry> findByCourierTypeIgnoreCase(String courierType);

    Optional<MonthlyCourierEntry> findFirstByTrackingNumberIgnoreCase(String trackingNumber);

    List<MonthlyCourierEntry> findByTrackingNumberContainingIgnoreCase(String trackingNumber);

    List<MonthlyCourierEntry> findByConsigneeAddressContainingIgnoreCase(String fragment);

    List<MonthlyCourierEntry> findByCourierTypeContainingIgnoreCase(String fragment);

    void deleteByMonthlyQuotationId(String monthlyQuotationId);

    boolean existsByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumberAndIdNot(String trackingNumber, String id);
}

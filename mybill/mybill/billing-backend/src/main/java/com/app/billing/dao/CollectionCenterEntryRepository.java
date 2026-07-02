package com.app.billing.dao;

import com.app.billing.model.CollectionCenterEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionCenterEntryRepository extends MongoRepository<CollectionCenterEntry, String> {

    List<CollectionCenterEntry> findByCollectionCustomerIdOrderByEntryDateDesc(String collectionCustomerId);

    void deleteByCollectionCustomerId(String collectionCustomerId);

    Optional<CollectionCenterEntry> findFirstByAwbNoIgnoreCase(String awbNo);

    List<CollectionCenterEntry> findByEntryDateBetween(LocalDate start, LocalDate end);

    List<CollectionCenterEntry> findByEntryDate(LocalDate entryDate);

    List<CollectionCenterEntry> findByEntryDateGreaterThanEqual(LocalDate start);

    List<CollectionCenterEntry> findByEntryDateLessThanEqual(LocalDate end);

    List<CollectionCenterEntry> findByCourierIgnoreCase(String courier);
}

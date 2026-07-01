package com.app.billing.dao;

import com.app.billing.model.AccountingEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AccountingEntryRepository extends MongoRepository<AccountingEntry, String> {

    Optional<AccountingEntry> findTopByOrderByEntryDateDescCreatedAtDesc();

    List<AccountingEntry> findAllByOrderByEntryDateAscCreatedAtAsc();
}

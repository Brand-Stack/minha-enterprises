package com.app.billing.dao;

import com.app.billing.model.CompanySettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanySettingsRepository extends MongoRepository<CompanySettings, String> {
    // There should be only one company settings record
    Optional<CompanySettings> findFirstByOrderByCreatedAtDesc();
}


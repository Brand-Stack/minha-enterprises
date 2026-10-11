package com.app.billing.dao;

import com.app.billing.model.ModuleRegistry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleRegistryRepository extends MongoRepository<ModuleRegistry, String> {
    Optional<ModuleRegistry> findByModuleKey(String moduleKey);
    List<ModuleRegistry> findAllByOrderBySortOrderAsc();
}

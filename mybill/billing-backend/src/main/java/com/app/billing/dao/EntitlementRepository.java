package com.app.billing.dao;

import com.app.billing.model.Entitlement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EntitlementRepository extends MongoRepository<Entitlement, String> {
    Optional<Entitlement> findByCategoryId(String categoryId);
    void deleteByCategoryId(String categoryId);
}

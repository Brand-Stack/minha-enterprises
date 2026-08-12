package com.app.billing.dao;

import com.app.billing.model.EmployeeCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeCategoryRepository extends MongoRepository<EmployeeCategory, String> {
    Optional<EmployeeCategory> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}

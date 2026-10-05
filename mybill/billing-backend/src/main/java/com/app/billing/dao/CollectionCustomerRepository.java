package com.app.billing.dao;

import com.app.billing.model.CollectionCustomer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CollectionCustomerRepository extends MongoRepository<CollectionCustomer, String> {

    boolean existsByCustomerCode(String customerCode);

    Optional<CollectionCustomer> findFirstByCustomerCodeStartingWithOrderByCustomerCodeDesc(String prefix);
}

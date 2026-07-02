package com.app.billing.dao;

import com.app.billing.model.CollectionCustomerAwb;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionCustomerAwbRepository extends MongoRepository<CollectionCustomerAwb, String> {

    Optional<CollectionCustomerAwb> findFirstByAwbNoIgnoreCase(String awbNo);

    List<CollectionCustomerAwb> findByCollectionCustomerIdAndStatusOrderByCreatedAtDesc(
            String collectionCustomerId, String status);

    long countByCollectionCustomerIdAndStatus(String collectionCustomerId, String status);

    List<CollectionCustomerAwb> findByCollectionCustomerIdOrderByCreatedAtDesc(String collectionCustomerId);

    Optional<CollectionCustomerAwb> findByCollectionCenterEntryId(String collectionCenterEntryId);

    void deleteByCollectionCustomerId(String collectionCustomerId);

    long countByStatus(String status);
}

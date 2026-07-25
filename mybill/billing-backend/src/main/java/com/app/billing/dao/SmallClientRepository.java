package com.app.billing.dao;

import com.app.billing.model.SmallClient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmallClientRepository extends MongoRepository<SmallClient, String> {
    Optional<SmallClient> findByPartyCode(String partyCode);
    boolean existsByPartyCode(String partyCode);

    @Query("{'$or': [{'partyName': {$regex: ?0, $options: 'i'}}, {'partyCode': {$regex: ?0, $options: 'i'}}, {'contactPerson': {$regex: ?0, $options: 'i'}}, {'phone': {$regex: ?0, $options: 'i'}}, {'gstin': {$regex: ?0, $options: 'i'}}]}")
    List<SmallClient> searchSmallClients(String searchTerm);

    List<SmallClient> findByPartyType(SmallClient.SmallClientType partyType);

    @Query("{'partyType': {$in: ['SUPPLIER', 'CUSTOMER', 'BOTH']}}")
    List<SmallClient> findSmallClientsForPurchase();
}

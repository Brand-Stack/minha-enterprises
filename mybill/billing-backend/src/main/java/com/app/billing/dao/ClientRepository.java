package com.app.billing.dao;

import com.app.billing.model.Client;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends MongoRepository<Client, String> {
    Optional<Client> findByPartyCode(String partyCode);
    boolean existsByPartyCode(String partyCode);

    @Query("{'$or': [{'partyName': {$regex: ?0, $options: 'i'}}, {'partyCode': {$regex: ?0, $options: 'i'}}, {'contactPerson': {$regex: ?0, $options: 'i'}}, {'phone': {$regex: ?0, $options: 'i'}}, {'gstin': {$regex: ?0, $options: 'i'}}]}")
    List<Client> searchClients(String searchTerm);

    List<Client> findByPartyType(Client.ClientType partyType);

    @Query("{'partyType': {$in: ['SUPPLIER', 'CUSTOMER', 'BOTH']}}")
    List<Client> findClientsForPurchase();
}

package com.app.billing.dao;

import com.app.billing.model.Item;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends MongoRepository<Item, String> {
    Optional<Item> findByItemCode(String itemCode);
    Optional<Item> findByEnCode(String enCode);
    boolean existsByItemCode(String itemCode);
    boolean existsByEnCode(String enCode);
    
    @Query("{'$or': [{'itemName': {$regex: ?0, $options: 'i'}}, {'itemCode': {$regex: ?0, $options: 'i'}}, {'enCode': {$regex: ?0, $options: 'i'}}, {'category': {$regex: ?0, $options: 'i'}}]}")
    List<Item> searchItems(String searchTerm);
    
    List<Item> findByStockQuantityLessThan(BigDecimal minStockLevel);
}


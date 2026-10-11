package com.app.billing.dao;

import com.app.billing.model.ItemTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemTransactionRepository extends MongoRepository<ItemTransaction, String> {
    List<ItemTransaction> findByItemId(String itemId);
    List<ItemTransaction> findByTransactionType(ItemTransaction.TransactionType transactionType);
}


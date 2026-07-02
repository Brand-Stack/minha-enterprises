package com.app.billing.dao;

import com.app.billing.model.MasterData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterDataRepository extends MongoRepository<MasterData, String> {
    List<MasterData> findByType(MasterData.MasterDataType type);
    List<MasterData> findByTypeAndActive(MasterData.MasterDataType type, Boolean active);
    
    @Query("{'$or': [{'name': {$regex: ?0, $options: 'i'}}, {'description': {$regex: ?0, $options: 'i'}}], 'type': ?1}")
    List<MasterData> searchByType(String searchTerm, MasterData.MasterDataType type);
}


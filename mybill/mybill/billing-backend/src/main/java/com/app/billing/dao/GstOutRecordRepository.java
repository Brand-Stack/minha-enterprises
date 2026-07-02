package com.app.billing.dao;

import com.app.billing.model.GstOutRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GstOutRecordRepository extends MongoRepository<GstOutRecord, String> {
}

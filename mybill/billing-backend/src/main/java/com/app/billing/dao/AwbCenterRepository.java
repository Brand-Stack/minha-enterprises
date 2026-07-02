package com.app.billing.dao;

import com.app.billing.model.AwbCenterRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AwbCenterRepository extends MongoRepository<AwbCenterRecord, String> {

    Optional<AwbCenterRecord> findFirstByAwbNumberIgnoreCase(String awbNumber);

    boolean existsByAwbNumberIgnoreCase(String awbNumber);

    long countByQueueStatus(String queueStatus);
}

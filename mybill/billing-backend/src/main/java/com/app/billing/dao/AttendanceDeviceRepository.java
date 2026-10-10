package com.app.billing.dao;

import com.app.billing.model.AttendanceDevice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceDeviceRepository extends MongoRepository<AttendanceDevice, String> {
    Optional<AttendanceDevice> findByDeviceId(String deviceId);
}

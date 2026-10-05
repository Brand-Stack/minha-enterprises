package com.app.billing.dao;

import com.app.billing.model.DeviceEmployeeMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceEmployeeMappingRepository extends MongoRepository<DeviceEmployeeMapping, String> {
    Optional<DeviceEmployeeMapping> findByDeviceIdAndDeviceUserId(String deviceId, String deviceUserId);
    Optional<DeviceEmployeeMapping> findByDeviceUserId(String deviceUserId);
    List<DeviceEmployeeMapping> findByEmployeeId(String employeeId);
}

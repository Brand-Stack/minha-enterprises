package com.app.billing.dao;

import com.app.billing.model.LeaveEntitlement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveEntitlementRepository extends MongoRepository<LeaveEntitlement, String> {
    Optional<LeaveEntitlement> findByEmployeeIdAndYear(String employeeId, Integer year);
    List<LeaveEntitlement> findByYear(Integer year);
}

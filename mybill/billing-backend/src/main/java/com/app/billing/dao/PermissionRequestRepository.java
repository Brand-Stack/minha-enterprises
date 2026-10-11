package com.app.billing.dao;

import com.app.billing.model.PermissionRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRequestRepository extends MongoRepository<PermissionRequest, String> {
    List<PermissionRequest> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);
    List<PermissionRequest> findByEmployeeIdAndDate(String employeeId, LocalDate date);
    Optional<PermissionRequest> findByEmployeeIdAndDateAndStatus(String employeeId, LocalDate date, PermissionRequest.PermissionStatus status);
    List<PermissionRequest> findByStatus(PermissionRequest.PermissionStatus status);
    List<PermissionRequest> findByDateBetween(LocalDate startDate, LocalDate endDate);
}

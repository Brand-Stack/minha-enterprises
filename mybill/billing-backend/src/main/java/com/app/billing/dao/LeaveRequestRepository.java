package com.app.billing.dao;

import com.app.billing.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends MongoRepository<LeaveRequest, String> {
    List<LeaveRequest> findByEmployeeIdOrderByAppliedDateDesc(String employeeId);
    Page<LeaveRequest> findByEmployeeId(String employeeId, Pageable pageable);
    List<LeaveRequest> findByStatus(LeaveRequest.LeaveStatus status);
    List<LeaveRequest> findByEmployeeIdAndStatus(String employeeId, LeaveRequest.LeaveStatus status);
    List<LeaveRequest> findByEmployeeIdAndStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            String employeeId, LeaveRequest.LeaveStatus status, LocalDate date1, LocalDate date2);
    List<LeaveRequest> findByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            LeaveRequest.LeaveStatus status, LocalDate date1, LocalDate date2);
}

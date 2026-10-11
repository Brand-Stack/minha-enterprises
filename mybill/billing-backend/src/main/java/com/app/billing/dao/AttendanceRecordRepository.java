package com.app.billing.dao;

import com.app.billing.model.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends MongoRepository<AttendanceRecord, String> {
    Optional<AttendanceRecord> findByEmployeeIdAndDate(String employeeId, LocalDate date);

    @Query(value = "{'employeeId': ?0, 'date': {$gte: ?1, $lte: ?2}}", sort = "{'date': 1}")
    List<AttendanceRecord> findByEmployeeIdAndDateBetweenOrderByDateAsc(String employeeId, LocalDate startDate, LocalDate endDate);

    @Query(value = "{'date': {$gte: ?0, $lte: ?1}}", sort = "{'date': 1}")
    List<AttendanceRecord> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);

    @Query("{'date': {$gte: ?0, $lte: ?1}}")
    List<AttendanceRecord> findByDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("{'date': {$gte: ?0, $lte: ?1}}")
    Page<AttendanceRecord> findByDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<AttendanceRecord> findByDate(LocalDate date);
    long countByDateAndStatus(LocalDate date, AttendanceRecord.AttendanceStatus status);
}

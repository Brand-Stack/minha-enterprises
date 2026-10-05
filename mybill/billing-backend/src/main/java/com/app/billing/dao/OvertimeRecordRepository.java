package com.app.billing.dao;

import com.app.billing.model.OvertimeRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OvertimeRecordRepository extends MongoRepository<OvertimeRecord, String> {
    Optional<OvertimeRecord> findByEmployeeIdAndDate(String employeeId, LocalDate date);

    @Query("{'employeeId': ?0, 'date': {$gte: ?1, $lte: ?2}}")
    List<OvertimeRecord> findByEmployeeIdAndDateBetween(String employeeId, LocalDate startDate, LocalDate endDate);

    @Query("{'date': {$gte: ?0, $lte: ?1}}")
    List<OvertimeRecord> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<OvertimeRecord> findByStatus(OvertimeRecord.OvertimeStatus status);
}

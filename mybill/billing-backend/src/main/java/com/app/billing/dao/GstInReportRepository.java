package com.app.billing.dao;

import com.app.billing.model.GstInReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GstInReportRepository extends MongoRepository<GstInReport, String> {
    Optional<GstInReport> findByMonthIgnoreCaseAndYear(String month, Integer year);
}

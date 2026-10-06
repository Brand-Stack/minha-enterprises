package com.app.billing.dao;

import com.app.billing.model.Holiday;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends MongoRepository<Holiday, String> {
    List<Holiday> findByYearOrderByHolidayDateAsc(int year);
    List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate startDate, LocalDate endDate);
    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);
    List<Holiday> findByActiveTrue();
}

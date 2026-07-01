package com.app.billing.dao;

import com.app.billing.model.CashBooking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashBookingRepository extends MongoRepository<CashBooking, String> {

    Optional<CashBooking> findFirstByAwbNoIgnoreCase(String awbNo);

    List<CashBooking> findByBookingDateBetween(LocalDate start, LocalDate end);

    List<CashBooking> findByBookingDate(LocalDate bookingDate);

    List<CashBooking> findByBookingDateGreaterThanEqual(LocalDate start);

    List<CashBooking> findByBookingDateLessThanEqual(LocalDate end);

    List<CashBooking> findByCourierIgnoreCase(String courier);
}

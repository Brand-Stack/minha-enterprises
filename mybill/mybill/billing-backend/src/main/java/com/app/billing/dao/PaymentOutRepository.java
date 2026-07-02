package com.app.billing.dao;

import com.app.billing.model.PaymentOut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOutRepository extends MongoRepository<PaymentOut, String> {
    Optional<PaymentOut> findByReceiptNumber(String receiptNumber);
    List<PaymentOut> findByDateBetween(LocalDate startDate, LocalDate endDate);
    Page<PaymentOut> findByDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    List<PaymentOut> findByPartyId(String partyId);
}


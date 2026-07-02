package com.app.billing.dao;

import com.app.billing.model.PurchaseBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseBillRepository extends MongoRepository<PurchaseBill, String> {
    Optional<PurchaseBill> findByBillNumber(String billNumber);
    List<PurchaseBill> findByBillDateBetween(LocalDate startDate, LocalDate endDate);
    Page<PurchaseBill> findByBillDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    List<PurchaseBill> findByPartyId(String partyId);
    Page<PurchaseBill> findByPaymentStatus(PurchaseBill.PaymentStatus paymentStatus, Pageable pageable);
    List<PurchaseBill> findByPaymentStatus(PurchaseBill.PaymentStatus paymentStatus);
}


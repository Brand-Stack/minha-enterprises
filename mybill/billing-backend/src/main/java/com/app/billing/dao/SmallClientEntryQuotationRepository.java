package com.app.billing.dao;

import com.app.billing.model.SmallClientEntryQuotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmallClientEntryQuotationRepository extends MongoRepository<SmallClientEntryQuotation, String> {

    Page<SmallClientEntryQuotation> findByShopId(String shopId, Pageable pageable);

    @Query("{ 'shopId': ?0, 'customerId': ?1, 'month': ?2, 'year': ?3 }")
    Optional<SmallClientEntryQuotation> findByShopIdAndCustomerAndMonthAndYear(String shopId, String customerId,
            String month, Integer year);

    @Query("{ 'shopId': ?0, 'id': ?1 }")
    Optional<SmallClientEntryQuotation> findByIdAndShopId(String id, String shopId);

    long countByShopIdAndYearAndMonth(String shopId, Integer year, String month);

    /** Find all quotations whose invoice number matches the given regex (e.g. for FY 2026-27: ^\\d{4}/2026-27$). */
    @Query("{ 'invoiceNumber': { $regex: ?0 } }")
    List<SmallClientEntryQuotation> findByInvoiceNumberRegex(String regex);
}



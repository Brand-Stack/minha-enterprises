package com.app.billing.dao;

import com.app.billing.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends MongoRepository<Invoice, String> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    boolean existsByInvoiceNumber(String invoiceNumber);
    
    /**
     * Check if invoice number exists for a specific bill type
     * Used for uniqueness validation per series
     */
    boolean existsByInvoiceNumberAndBillType(String invoiceNumber, Invoice.BillType billType);
    
    /**
     * Find invoices in date range. Uses $gte and $lte for INCLUSIVE bounds.
     * Spring Data's default Between may use exclusive bounds - explicit query ensures
     * invoices on startDate and endDate are included.
     */
    @Query("{'invoiceDate': {$gte: ?0, $lte: ?1}}")
    List<Invoice> findByInvoiceDateBetween(LocalDate startDate, LocalDate endDate);
    List<Invoice> findByPartyId(String partyId);
    
    /**
     * Find invoices by bill type (for series-specific queries)
     */
    List<Invoice> findByBillType(Invoice.BillType billType);
}

package com.app.billing.dao;

import com.app.billing.model.Quotation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuotationRepository extends MongoRepository<Quotation, String> {
    Optional<Quotation> findByQuotationNumber(String quotationNumber);
    boolean existsByQuotationNumber(String quotationNumber);
    
    List<Quotation> findByPartyId(String partyId);
    List<Quotation> findByQuotationDateBetween(LocalDate startDate, LocalDate endDate);
    
    @Query("{'$or': [{'quotationNumber': {$regex: ?0, $options: 'i'}}, {'partyName': {$regex: ?0, $options: 'i'}}]}")
    List<Quotation> searchQuotations(String searchTerm);
}


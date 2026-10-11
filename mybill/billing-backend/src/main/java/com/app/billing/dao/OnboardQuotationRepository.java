package com.app.billing.dao;

import com.app.billing.model.CourierQuotation.QuotationStatus;
import com.app.billing.model.OnboardQuotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface OnboardQuotationRepository extends MongoRepository<OnboardQuotation, String> {

    Optional<OnboardQuotation> findByQuotationNumber(String quotationNumber);

    Page<OnboardQuotation> findByCustomerNameContainingIgnoreCaseOrQuotationNumberContainingIgnoreCase(
            String customerName, String quotationNumber, Pageable pageable);

    @Query("{ 'effectiveDate': { $gte: ?0, $lte: ?1 } }")
    Page<OnboardQuotation> findByEffectiveDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    @Query("{\n" +
           "  $and: [\n" +
           "    { $or: [ { 'effectiveDate': null }, { 'effectiveDate': { $gte: ?0 } } ] },\n" +
           "    { $or: [ { 'effectiveDate': null }, { 'effectiveDate': { $lte: ?1 } } ] },\n" +
           "    { $or: [ { 'status': null }, { 'status': ?2 } ] },\n" +
           "    { $or: [\n" +
           "        { 'customerName': { $regex: ?3, $options: 'i' } },\n" +
           "        { 'quotationNumber': { $regex: ?3, $options: 'i' } },\n" +
           "        { 'branchName': { $regex: ?3, $options: 'i' } }\n" +
           "    ] }\n" +
           "  ]\n" +
           "}")
    Page<OnboardQuotation> findWithFiltersAndSearch(LocalDate effectiveFrom, LocalDate effectiveTo,
                                                   QuotationStatus status, String regex, Pageable pageable);
}

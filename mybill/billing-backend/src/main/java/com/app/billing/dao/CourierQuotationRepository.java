package com.app.billing.dao;

import com.app.billing.model.CourierQuotation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourierQuotationRepository extends MongoRepository<CourierQuotation, String> {
    List<CourierQuotation> findByCustomerId(String customerId);

    List<CourierQuotation> findByCustomerIdAndStatus(String customerId, CourierQuotation.QuotationStatus status);

    List<CourierQuotation> findByStatus(CourierQuotation.QuotationStatus status);

    Optional<CourierQuotation> findByQuotationNumber(String quotationNumber);

    boolean existsByQuotationNumber(String quotationNumber);

    boolean existsByCustomerIdAndStatus(String customerId, CourierQuotation.QuotationStatus status);

    @Query("{'$or': [{'quotationNumber': {$regex: ?0, $options: 'i'}}, {'customerName': {$regex: ?0, $options: 'i'}}, {'branchName': {$regex: ?0, $options: 'i'}}]}")
    List<CourierQuotation> searchCourierQuotations(String searchTerm);

    @Query("{ $and: [ " +
            "{ $or: [ { $expr: { $eq: ['?0', 'null'] } }, { 'customerId': ?0 } ] }, " +
            "{ $or: [ { $expr: { $eq: ['?1', 'null'] } }, { 'effectiveDate': { $gte: ?1 } } ] }, " +
            "{ $or: [ { $expr: { $eq: ['?2', 'null'] } }, { 'effectiveDate': { $lte: ?2 } } ] }, " +
            "{ $or: [ { $expr: { $eq: ['?3', 'null'] } }, { 'status': ?3 } ] } " +
            "] }")
    List<CourierQuotation> findQuotationsForReport(String customerId, java.time.LocalDate startDate,
            java.time.LocalDate endDate, String status);

    List<CourierQuotation> findByEffectiveDateBetween(java.time.LocalDate start, java.time.LocalDate end);

    @Query("{ $and: [ " +
            "{ $or: [ { $expr: { $eq: ['?0', 'null'] } }, { 'effectiveDate': { $gte: ?0 } } ] }, " +
            "{ $or: [ { $expr: { $eq: ['?1', 'null'] } }, { 'effectiveDate': { $lte: ?1 } } ] }, " +
            "{ $or: [ { $expr: { $eq: ['?2', 'null'] } }, { 'status': ?2 } ] } " +
            "] }")
    List<CourierQuotation> findWithFilters(java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo, String status);
}

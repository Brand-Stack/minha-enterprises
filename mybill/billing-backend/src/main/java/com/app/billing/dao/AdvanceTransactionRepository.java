package com.app.billing.dao;

import com.app.billing.model.AdvanceTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdvanceTransactionRepository extends MongoRepository<AdvanceTransaction, String> {
    List<AdvanceTransaction> findByEmployeeIdOrderByTransactionDateDescCreatedAtDesc(String employeeId);
    List<AdvanceTransaction> findByAdvanceAccountIdOrderByTransactionDateDescCreatedAtDesc(String advanceAccountId);
    Optional<AdvanceTransaction> findByPayslipIdAndTransactionType(String payslipId, AdvanceTransaction.TransactionType type);
    List<AdvanceTransaction> findAllByPayslipIdAndTransactionType(String payslipId, AdvanceTransaction.TransactionType type);
    List<AdvanceTransaction> findAllByPayslipId(String payslipId);
    List<AdvanceTransaction> findByEmployeeIdAndPayrollMonth(String employeeId, String payrollMonth);
}

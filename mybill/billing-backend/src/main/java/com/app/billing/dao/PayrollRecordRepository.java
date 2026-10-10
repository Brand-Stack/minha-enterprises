package com.app.billing.dao;

import com.app.billing.model.PayrollRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRecordRepository extends MongoRepository<PayrollRecord, String> {
    Optional<PayrollRecord> findByEmployeeIdAndPayrollMonth(String employeeId, String payrollMonth);
    Optional<PayrollRecord> findFirstByEmployeeIdAndPayrollMonthLessThanOrderByPayrollMonthDesc(String employeeId, String payrollMonth);
    List<PayrollRecord> findByPayrollMonth(String payrollMonth);
    List<PayrollRecord> findByEmployeeIdOrderByPayrollMonthDesc(String employeeId);
    Optional<PayrollRecord> findByPayslipNumber(String payslipNumber);
}

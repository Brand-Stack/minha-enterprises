package com.app.billing.dao;

import com.app.billing.model.EmployeeBonus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeBonusRepository extends MongoRepository<EmployeeBonus, String> {
    List<EmployeeBonus> findByEmployeeIdOrderByPaymentDateDesc(String employeeId);
    List<EmployeeBonus> findByEmployeeIdAndPayrollMonth(String employeeId, String payrollMonth);
    List<EmployeeBonus> findByPayrollMonth(String payrollMonth);
    List<EmployeeBonus> findByBonusYear(Integer bonusYear);
}

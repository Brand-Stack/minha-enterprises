package com.app.billing.dao;

import com.app.billing.model.EmployeeAdvanceAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeAdvanceAccountRepository extends MongoRepository<EmployeeAdvanceAccount, String> {
    Optional<EmployeeAdvanceAccount> findByEmployeeId(String employeeId);
}

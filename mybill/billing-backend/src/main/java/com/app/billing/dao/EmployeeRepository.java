package com.app.billing.dao;

import com.app.billing.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    /** Use first match when legacy data has duplicate emails (findByEmail would throw). */
    Optional<Employee> findFirstByEmailOrderByIdAsc(String email);
    Optional<Employee> findByEmployeeCode(String employeeCode);
    boolean existsByEmail(String email);

    /** Resolve login by email (user id) or employee code. */
    default Optional<Employee> findByLoginId(String loginId) {
        Optional<Employee> byEmail = findFirstByEmailOrderByIdAsc(loginId);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return findByEmployeeCode(loginId);
    }
    boolean existsByEmployeeCode(String employeeCode);
    
    @Query("{'$or': [{'employeeCode': {$regex: ?0, $options: 'i'}}, {'employeeName': {$regex: ?0, $options: 'i'}}, {'email': {$regex: ?0, $options: 'i'}}, {'phone': {$regex: ?0, $options: 'i'}}, {'category': {$regex: ?0, $options: 'i'}}, {'designation': {$regex: ?0, $options: 'i'}}]}")
    List<Employee> searchEmployees(String searchTerm);
}


package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "employees")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Employee extends BaseEntity {
    private String employeeCode;
    private String employeeName;
    private String category; // Manager, General, Store, or custom
    private String designation;
    private Gender gender;
    private LocalDate dateOfBirth;
    private LocalDate dateOfJoining;
    private String phone;
    private String email;
    private String address;
    private Status status; // Active, Inactive
    private String password;
    private Role role; // For authentication/authorization
    private String categoryId; // References EmployeeCategory._id (RBAC role/category)

    public enum Gender {
        MALE, FEMALE, OTHER
    }
    
    public enum Status {
        ACTIVE, INACTIVE
    }
    
    /**
     * System role. ADMIN has full unrestricted access; all other (employee) roles are
     * entitlement-driven via the assigned {@code categoryId}. Legacy values are retained
     * so existing employee records continue to deserialize and are treated as non-admin.
     */
    public enum Role {
        ADMIN, EMPLOYEE, BILLING_USER, PARTY_USER, BILLER, CASHIER
    }
}


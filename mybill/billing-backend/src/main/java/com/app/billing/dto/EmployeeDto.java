package com.app.billing.dto;

import com.app.billing.model.Employee;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDto {
    private String id;
    
    private String employeeCode;
    
    @NotBlank(message = "Employee name is required")
    private String employeeName;
    
    private String category; // Manager, General, Store, or custom
    private String designation;
    private Employee.Gender gender;
    private LocalDate dateOfBirth;
    private LocalDate dateOfJoining;
    private String phone;
    private String email;
    private String address;
    private Employee.Status status;
    private String password; // Only for creation/update, not returned in responses
    private Employee.Role role;
    private String categoryId; // RBAC category/role id (references EmployeeCategory)
    private String categoryName; // Resolved category name (read-only, for display)
    private String lastUpdatedBy; // EmployeeCode of user who last updated
}


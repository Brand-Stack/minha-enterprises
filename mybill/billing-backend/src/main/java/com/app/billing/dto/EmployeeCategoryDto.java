package com.app.billing.dto;

import com.app.billing.model.EmployeeCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCategoryDto {
    private String id;

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;
    private EmployeeCategory.Status status;
    private String lastUpdatedBy;
}

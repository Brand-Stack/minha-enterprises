package com.app.billing.dto;

import com.app.billing.model.MasterData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MasterDataDto {
    private String id;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Type is required")
    private MasterData.MasterDataType type;
    
    private Boolean active;
    
    private String role; // Super Admin, Manager, General Staff, Supervisor (required only for EMPLOYEE_CATEGORY)
    private String lastUpdatedBy; // EmployeeCode of user who last updated
}


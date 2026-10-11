package com.app.billing.dto;

import com.app.billing.model.CourierQuotation.QuotationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardQuotationDto {

    private String id;
    private String quotationNumber;
    
    @NotBlank(message = "Customer name is required")
    private String customerName;
    private String branchName;
    
    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;
    
    @NotNull(message = "Valid till date is required")
    private LocalDate validTillDate;
    
    private String remarks;
    private QuotationStatus status;

    private Double fuelChargePercentage;
    private Double fovCharges;
    private String selectedBankAccountId;

    @Builder.Default
    private List<OnboardSlabDto> slabs = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnboardSlabDto {
        private String slabId;
        private String slabName;
        private Boolean selected;
        @Builder.Default
        private List<ZoneRateConfigDto> zoneRates = new ArrayList<>();
    }
}

package com.app.billing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingEntryDto {
    private String id;

    @NotNull(message = "Entry date is required")
    private LocalDate entryDate;

    /** IN or OUT (JSON may use {@code type} as alias). */
    @NotBlank(message = "Entry type (IN or OUT) is required")
    @JsonAlias("type")
    private String entryType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String description;
    private BigDecimal balanceAfter;
    private String createdBy;
    private String lastUpdatedBy;
}

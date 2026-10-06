package com.app.billing.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkAwbRegisterRequestDto {
    @NotEmpty(message = "Provide at least one AWB number")
    private List<String> awbNumbers;
}

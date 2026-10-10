package com.app.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwbBatchValidationResultDto {
    private boolean valid;

    @Builder.Default
    private List<String> duplicateInRequest = new ArrayList<>();

    @Builder.Default
    private List<String> invalidFormat = new ArrayList<>();

    @Builder.Default
    private List<AwbConflictLineDto> conflicts = new ArrayList<>();
}

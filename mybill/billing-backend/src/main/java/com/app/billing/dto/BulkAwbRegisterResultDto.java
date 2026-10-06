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
public class BulkAwbRegisterResultDto {

    @Builder.Default
    private List<CollectionCustomerAwbDto> created = new ArrayList<>();

    /** Normalized AWBs that appeared more than once in the request. */
    @Builder.Default
    private List<String> duplicateInRequest = new ArrayList<>();

    @Builder.Default
    private List<String> invalidFormat = new ArrayList<>();

    /** Already registered for this collection customer (pending or used). */
    @Builder.Default
    private List<String> alreadyOnCustomer = new ArrayList<>();

    @Builder.Default
    private List<AwbConflictLineDto> conflicts = new ArrayList<>();
}

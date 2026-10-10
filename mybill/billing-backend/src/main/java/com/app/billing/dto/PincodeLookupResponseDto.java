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
public class PincodeLookupResponseDto {
    private boolean success;
    private String message;
    @Builder.Default
    private List<PincodeAreaDto> areas = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PincodeAreaDto {
        private String name;
        private String district;
        private String region;
        private String state;
    }
}

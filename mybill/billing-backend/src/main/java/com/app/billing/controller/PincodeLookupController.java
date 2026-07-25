package com.app.billing.controller;

import com.app.billing.dto.PincodeLookupResponseDto;
import com.app.billing.service.PincodeLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pincode")
@RequiredArgsConstructor
@Tag(name = "Pincode", description = "India pincode lookup (postalpincode.in)")
public class PincodeLookupController {

    private final PincodeLookupService pincodeLookupService;

    @GetMapping("/{pincode}")
    @Operation(summary = "Lookup area and city by 6-digit pincode")
    public ResponseEntity<PincodeLookupResponseDto> lookup(@PathVariable String pincode) {
        return ResponseEntity.ok(pincodeLookupService.lookup(pincode));
    }
}

package com.app.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/config")
@Tag(name = "UI Config", description = "Frontend defaults (year ranges, etc.)")
public class UiConfigController {

    @Value("${app.ui.collection-year-range-past:5}")
    private int collectionYearRangePast;

    @Value("${app.ui.collection-year-range-future:5}")
    private int collectionYearRangeFuture;

    @GetMapping("/ui")
    @Operation(summary = "UI defaults for forms and filters")
    public ResponseEntity<Map<String, Object>> uiDefaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("collectionYearRangePast", collectionYearRangePast);
        m.put("collectionYearRangeFuture", collectionYearRangeFuture);
        return ResponseEntity.ok(m);
    }
}

package com.app.billing.controller;

import com.app.billing.dto.AwbCenterBulkGenerateRequest;
import com.app.billing.dto.AwbCenterQueueStatsDto;
import com.app.billing.dto.AwbCenterRecordDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AwbCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/awb-center")
@RequiredArgsConstructor
@Tag(name = "AWB Center", description = "Central AWB master — pending/completed queues and bulk generation")
public class AwbCenterController {

    private final AwbCenterService service;

    @GetMapping
    @RequiresPermission(module = Modules.AWB_CENTER, action = Modules.VIEW)
    @Operation(summary = "Search AWB center records")
    public ResponseEntity<PageResponse<AwbCenterRecordDto>> search(
            @RequestParam(required = false) String awbNumber,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String queueStatus,
            @RequestParam(required = false) String queueType,
            @RequestParam(required = false) String awbType,
            @RequestParam(required = false) String courierType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean unmapped,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "awbNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        String qs = queueStatus != null ? queueStatus : status;
        String type = awbType != null ? awbType : queueType;
        return ResponseEntity.ok(service.search(awbNumber, qs, type, courierType, dateFrom, dateTo, unmapped, page, size, sortBy, sortDir));
    }

    @GetMapping("/stats")
    @Operation(summary = "Queue counters")
    public ResponseEntity<AwbCenterQueueStatsDto> stats(
            @RequestParam(required = false) String courierType) {
        return ResponseEntity.ok(service.queueStats(courierType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AwbCenterRecordDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @RequiresPermission(module = Modules.AWB_CENTER, action = "GENERATE_BULK_AWBS")
    @Operation(summary = "Add single AWB")
    public ResponseEntity<AwbCenterRecordDto> create(@RequestBody Map<String, Object> body) {
        String awb = body != null ? stringVal(body.get("awbNumber")) : null;
        if (awb == null && body != null) {
            awb = stringVal(body.get("awb"));
        }
        Boolean collectionAwb = body != null ? boolVal(body.get("collectionCustomerAwb")) : null;
        String customerId = body != null ? stringVal(body.get("collectionCustomerId")) : null;
        String customerName = body != null ? stringVal(body.get("collectionCustomerName")) : null;
        String courierType = body != null ? stringVal(body.get("courierType")) : null;
        return new ResponseEntity<>(service.createSingle(awb, collectionAwb, customerId, customerName, courierType), HttpStatus.CREATED);
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static Boolean boolVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(o));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AwbCenterRecordDto> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        String awb = body != null ? body.get("awbNumber") : null;
        return ResponseEntity.ok(service.update(id, awb));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.AWB_CENTER, action = "DELETE_AWBS")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-generate")
    @RequiresPermission(module = Modules.AWB_CENTER, action = "GENERATE_BULK_AWBS")
    public ResponseEntity<Map<String, Object>> bulkGenerate(@Valid @RequestBody AwbCenterBulkGenerateRequest request) {
        int created = service.bulkGenerate(request);
        return ResponseEntity.ok(Map.of("createdCount", created, "message", created + " AWB(s) generated"));
    }

    @PostMapping("/sync-queues")
    @Operation(summary = "Sync pending AWBs to completed based on module usage")
    public ResponseEntity<Map<String, Object>> syncQueues() {
        int updated = service.syncAllQueuesFromModules();
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }

    @PostMapping("/delete-all-pending")
    @RequiresPermission(module = Modules.AWB_CENTER, action = "DELETE_AWBS")
    @Operation(summary = "Delete all pending AWBs from AWB Center and collection customer registry")
    public ResponseEntity<Map<String, Object>> deleteAllPending() {
        long deleted = service.deleteAllPendingAwbs();
        String message = deleted > 0
                ? (deleted + " pending AWBs deleted successfully")
                : "No pending AWBs to delete";
        return ResponseEntity.ok(Map.of("deletedCount", deleted, "message", message));
    }

    @GetMapping("/validate/{awb}")
    @Operation(summary = "Check if AWB exists in center")
    public ResponseEntity<Map<String, Boolean>> validate(@PathVariable String awb) {
        try {
            service.assertExistsInCenter(awb);
            return ResponseEntity.ok(Map.of("valid", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(Map.of("valid", false));
        }
    }
}

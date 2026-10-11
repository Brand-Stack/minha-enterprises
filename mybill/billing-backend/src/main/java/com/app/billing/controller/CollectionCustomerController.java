package com.app.billing.controller;

import com.app.billing.dto.AwbBatchValidationResultDto;
import com.app.billing.dto.AwbValidateRequestDto;
import com.app.billing.dto.BulkAwbRegisterRequestDto;
import com.app.billing.dto.BulkAwbRegisterResultDto;
import com.app.billing.dto.CollectionCustomerAwbDto;
import com.app.billing.dto.CollectionCustomerDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.CollectionCustomerAwbService;
import com.app.billing.service.CollectionCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/collection-customers", "/collection-customer"})
@RequiredArgsConstructor
@Tag(name = "Collection Customer", description = "Collection customer master (parent for collection center)")
public class CollectionCustomerController {

    private final CollectionCustomerService service;
    private final CollectionCustomerAwbService collectionCustomerAwbService;

    @PostMapping
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = Modules.CREATE)
    @Operation(summary = "Create collection customer")
    public ResponseEntity<CollectionCustomerDto> create(@Valid @RequestBody CollectionCustomerDto dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = Modules.EDIT)
    @Operation(summary = "Update collection customer")
    public ResponseEntity<CollectionCustomerDto> update(@PathVariable String id, @Valid @RequestBody CollectionCustomerDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get by id")
    public ResponseEntity<CollectionCustomerDto> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = Modules.VIEW)
    @Operation(summary = "List (paginated)")
    public ResponseEntity<PageResponse<CollectionCustomerDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(service.findAll(page, size, sortBy, sortDir));
    }

    @GetMapping("/search")
    @Operation(summary = "Search by name/code/phone/email")
    public ResponseEntity<PageResponse<CollectionCustomerDto>> search(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(service.search(term != null ? term : "", page, size, sortBy, sortDir));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = Modules.DELETE)
    @Operation(summary = "Delete customer and all collection center entries")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/awbs/validate")
    @Operation(summary = "Validate AWB numbers (duplicates in list, format, global uniqueness)")
    public ResponseEntity<AwbBatchValidationResultDto> validateAwbs(@RequestBody AwbValidateRequestDto body) {
        return ResponseEntity.ok(collectionCustomerAwbService.validateBatch(
                body.getCollectionCustomerId(),
                body.getAwbNumbers() != null ? body.getAwbNumbers() : List.of()));
    }

    @PostMapping("/{id}/awbs/bulk")
    @Operation(summary = "Register AWB numbers for this customer (PENDING until used on an entry). Partial success: check duplicateInRequest, conflicts, etc.")
    public ResponseEntity<BulkAwbRegisterResultDto> bulkRegisterAwbs(
            @PathVariable String id, @Valid @RequestBody BulkAwbRegisterRequestDto body) {
        BulkAwbRegisterResultDto out = collectionCustomerAwbService.registerBulkWithReport(id, body);
        boolean anyCreated = out.getCreated() != null && !out.getCreated().isEmpty();
        return new ResponseEntity<>(out, anyCreated ? HttpStatus.CREATED : HttpStatus.OK);
    }

    @GetMapping("/{id}/awbs")
    @Operation(summary = "List all registered AWBs for customer")
    public ResponseEntity<List<CollectionCustomerAwbDto>> listAwbs(@PathVariable String id) {
        return ResponseEntity.ok(collectionCustomerAwbService.listForCustomer(id));
    }

    @GetMapping("/{id}/awbs/pending")
    @Operation(summary = "List PENDING AWBs for customer")
    public ResponseEntity<List<CollectionCustomerAwbDto>> listPendingAwbs(@PathVariable String id) {
        return ResponseEntity.ok(collectionCustomerAwbService.listPendingForCustomer(id));
    }

    @DeleteMapping("/{id}/awbs/pending")
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = "CLEAR_PENDING_AWBS")
    @Operation(summary = "Clear all pending AWBs for customer (USED rows are preserved)")
    public ResponseEntity<Map<String, Object>> clearPendingAwbs(@PathVariable String id) {
        long deleted = collectionCustomerAwbService.clearPendingForCustomer(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deletedCount", deleted);
        out.put("message", "Pending AWBs cleared successfully");
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{customerId}/clear-pending-awbs/preview")
    @Operation(summary = "Preview total/pending AWBs and first pending rows for selected customer")
    public ResponseEntity<Map<String, Object>> previewPendingAwbs(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(collectionCustomerAwbService.previewPendingForCustomer(customerId, limit));
    }

    @PostMapping("/{customerId}/clear-pending-awbs")
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = "CLEAR_PENDING_AWBS")
    @Operation(summary = "Clear pending AWBs for selected customer (pending = not used in collection entry)")
    public ResponseEntity<Map<String, Object>> clearPendingAwbsBySelectedCustomer(@PathVariable String customerId) {
        long deleted = collectionCustomerAwbService.clearPendingForCustomer(customerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deletedCount", deleted);
        out.put("message", deleted > 0 ? (deleted + " pending AWBs cleared successfully") : "No pending AWBs to clear");
        return ResponseEntity.ok(out);
    }

    @PostMapping("/clear-pending-awbs")
    @RequiresPermission(module = Modules.COLLECTION_CUSTOMER, action = "CLEAR_PENDING_AWBS")
    @Operation(summary = "Clear all pending AWBs across collection customers (used AWBs are preserved)")
    public ResponseEntity<Map<String, Object>> clearAllPendingAwbs() {
        long deleted = collectionCustomerAwbService.clearPendingAcrossAllCustomers();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deletedCount", deleted);
        out.put("message", deleted > 0 ? (deleted + " pending AWBs cleared successfully") : "No pending AWBs to clear");
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/{customerId}/awbs/{registryId}")
    @Operation(summary = "Delete a PENDING registry AWB for this customer")
    public ResponseEntity<Void> deleteRegistryAwb(
            @PathVariable String customerId,
            @PathVariable String registryId) {
        collectionCustomerAwbService.deletePendingRegistryRow(customerId, registryId);
        return ResponseEntity.noContent().build();
    }
}

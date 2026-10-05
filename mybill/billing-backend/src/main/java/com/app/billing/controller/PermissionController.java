package com.app.billing.controller;

import com.app.billing.model.PermissionRequest;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AttendanceReportService;
import com.app.billing.service.PermissionEvaluatorService;
import com.app.billing.service.PermissionManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission Management", description = "Short duration permission requests and approvals")
public class PermissionController {

    private final PermissionManagementService permissionService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AttendanceReportService reportService;

    @PostMapping("/request")
    @Operation(summary = "Request Permission", description = "Apply for short permission (e.g. 1-2 hours)")
    public ResponseEntity<PermissionRequest> requestPermission(@RequestBody PermissionRequest request) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        if (!permissionEvaluatorService.isCurrentUserAdmin()) {
            request.setEmployeeId(current.getId());
        }

        return ResponseEntity.ok(permissionService.requestPermission(request));
    }

    @GetMapping("/my-requests")
    @Operation(summary = "My Permission Requests", description = "Get permission request history for logged-in employee")
    public ResponseEntity<List<PermissionRequest>> getMyPermissionRequests() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        return ResponseEntity.ok(permissionService.getPermissionHistoryForEmployee(current.getId()));
    }

    @GetMapping("/pending")
    @RequiresPermission(module = Modules.PERMISSIONS, action = "APPROVE_PERMISSION")
    @Operation(summary = "Pending Permission Requests", description = "Get list of pending permission requests")
    public ResponseEntity<List<PermissionRequest>> getPendingPermissions() {
        return ResponseEntity.ok(permissionService.getPendingPermissionRequests());
    }

    @GetMapping("/all")
    @RequiresPermission(module = Modules.PERMISSIONS, action = Modules.VIEW)
    @Operation(summary = "All Permission Requests", description = "Get all permission requests across organization")
    public ResponseEntity<List<PermissionRequest>> getAllPermissions(@RequestParam(required = false) String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        String targetId = !isAdmin && current != null ? current.getId() : employeeId;

        List<PermissionRequest> list = permissionService.getAllPermissionRequests();
        if (targetId != null && !targetId.trim().isEmpty()) {
            list = list.stream().filter(r -> targetId.equalsIgnoreCase(r.getEmployeeId())).toList();
        }
        return ResponseEntity.ok(list);
    }

    @PutMapping("/approve/{id}")
    @RequiresPermission(module = Modules.PERMISSIONS, action = "APPROVE_PERMISSION")
    @Operation(summary = "Approve or Reject Permission", description = "Admin approve or reject permission request")
    public ResponseEntity<PermissionRequest> approveOrRejectPermission(
            @PathVariable String id,
            @RequestBody ApproveRejectRequest request) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(permissionService.approveOrRejectPermission(id, request.isApprove(), request.getAdminRemarks(), adminUsername));
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.PERMISSIONS, action = Modules.EDIT)
    @Operation(summary = "Update Permission Request", description = "Update details of an existing permission request")
    public ResponseEntity<PermissionRequest> updatePermission(
            @PathVariable String id,
            @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(permissionService.updatePermission(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.PERMISSIONS, action = Modules.DELETE)
    @Operation(summary = "Delete Permission Request", description = "Delete a permission request")
    public ResponseEntity<Void> deletePermission(@PathVariable String id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export/excel")
    @RequiresPermission(module = Modules.PERMISSIONS, action = Modules.EXPORT)
    @Operation(summary = "Export Permission Requests Excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status) throws java.io.IOException {
        byte[] excel = reportService.generatePermissionReportExcel(employeeId, status);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Permission_Requests.xlsx")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/export/pdf")
    @RequiresPermission(module = Modules.PERMISSIONS, action = Modules.EXPORT)
    @Operation(summary = "Export Permission Requests PDF")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String status) {
        byte[] pdf = reportService.generatePermissionReportPdf(employeeId, status);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Permission_Requests.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Data
    public static class ApproveRejectRequest {
        private boolean approve;
        private String adminRemarks;
    }
}

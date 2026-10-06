package com.app.billing.controller;

import com.app.billing.model.LeaveEntitlement;
import com.app.billing.model.LeaveRequest;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AttendanceReportService;
import com.app.billing.service.LeaveManagementService;
import com.app.billing.service.PermissionEvaluatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
@Tag(name = "Leave Management", description = "Leave entitlement, requests and approvals")
public class LeaveController {

    private final LeaveManagementService leaveService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AttendanceReportService reportService;

    @GetMapping("/my-entitlements")
    @Operation(summary = "My Leave Entitlement", description = "Get leave balance for logged-in employee")
    public ResponseEntity<LeaveEntitlement> getMyEntitlement(@RequestParam(required = false) Integer year) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.getOrCreateEntitlement(current.getId(), y));
    }

    @GetMapping("/entitlements")
    @RequiresPermission(module = Modules.LEAVES, action = Modules.VIEW)
    @Operation(summary = "All Leave Entitlements", description = "Get all employee leave entitlements for year")
    public ResponseEntity<List<LeaveEntitlement>> getAllEntitlements(@RequestParam(required = false) Integer year) {
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.getAllEntitlementsForYear(y));
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply for Leave", description = "Submit a leave request")
    public ResponseEntity<LeaveRequest> applyLeave(@RequestBody LeaveRequest request) {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        // Non-admin can only apply for themselves
        if (!permissionEvaluatorService.isCurrentUserAdmin()) {
            request.setEmployeeId(current.getId());
        }

        return ResponseEntity.ok(leaveService.applyLeave(request));
    }

    @GetMapping("/my-requests")
    @Operation(summary = "My Leave Requests", description = "Get leave request history for logged-in employee")
    public ResponseEntity<List<LeaveRequest>> getMyLeaveRequests() {
        var current = permissionEvaluatorService.currentEmployee();
        if (current == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        return ResponseEntity.ok(leaveService.getLeaveHistoryForEmployee(current.getId()));
    }

    @GetMapping("/pending")
    @RequiresPermission(module = Modules.LEAVES, action = "APPROVE_LEAVE")
    @Operation(summary = "Pending Leave Requests", description = "Get list of pending leave requests")
    public ResponseEntity<List<LeaveRequest>> getPendingRequests() {
        return ResponseEntity.ok(leaveService.getPendingLeaveRequests());
    }

    @GetMapping("/all")
    @RequiresPermission(module = Modules.LEAVES, action = Modules.VIEW)
    @Operation(summary = "All Leave Requests", description = "Get all leave requests across organization")
    public ResponseEntity<List<LeaveRequest>> getAllRequests(@RequestParam(required = false) String employeeId) {
        var current = permissionEvaluatorService.currentEmployee();
        boolean isAdmin = permissionEvaluatorService.isCurrentUserAdmin();
        String targetId = !isAdmin && current != null ? current.getId() : employeeId;

        List<LeaveRequest> list = leaveService.getAllLeaveRequests();
        if (targetId != null && !targetId.trim().isEmpty()) {
            list = list.stream().filter(r -> targetId.equalsIgnoreCase(r.getEmployeeId())).toList();
        }
        return ResponseEntity.ok(list);
    }

    @PutMapping("/approve/{id}")
    @RequiresPermission(module = Modules.LEAVES, action = "APPROVE_LEAVE")
    @Operation(summary = "Approve or Reject Leave", description = "Admin approve or reject leave request")
    public ResponseEntity<LeaveRequest> approveOrRejectLeave(
            @PathVariable String id,
            @RequestBody ApproveRejectRequest request) {
        var current = permissionEvaluatorService.currentEmployee();
        String adminUsername = current != null ? current.getEmail() : "Admin";
        return ResponseEntity.ok(leaveService.approveOrRejectLeave(id, request.isApprove(), request.getAdminRemarks(), adminUsername));
    }

    @PutMapping("/{id}")
    @RequiresPermission(module = Modules.LEAVES, action = Modules.EDIT)
    @Operation(summary = "Update Leave Request", description = "Update details of an existing leave request")
    public ResponseEntity<LeaveRequest> updateLeave(
            @PathVariable String id,
            @RequestBody LeaveRequest request) {
        return ResponseEntity.ok(leaveService.updateLeave(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = Modules.LEAVES, action = Modules.DELETE)
    @Operation(summary = "Delete Leave Request", description = "Delete a leave request")
    public ResponseEntity<Void> deleteLeave(@PathVariable String id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export/excel")
    @RequiresPermission(module = Modules.LEAVES, action = Modules.EXPORT)
    @Operation(summary = "Export Leave Requests Excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false) String status) throws java.io.IOException {
        byte[] excel = reportService.generateLeaveReportExcel(employeeId, leaveType, status);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Leave_Requests.xlsx")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/export/pdf")
    @RequiresPermission(module = Modules.LEAVES, action = Modules.EXPORT)
    @Operation(summary = "Export Leave Requests PDF")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false) String status) {
        byte[] pdf = reportService.generateLeaveReportPdf(employeeId, leaveType, status);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Leave_Requests.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Data
    public static class ApproveRejectRequest {
        private boolean approve;
        private String adminRemarks;
    }
}

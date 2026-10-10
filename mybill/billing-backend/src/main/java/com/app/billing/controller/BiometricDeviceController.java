package com.app.billing.controller;

import com.app.billing.model.AttendanceDevice;
import com.app.billing.model.DeviceEmployeeMapping;
import com.app.billing.security.Modules;
import com.app.billing.security.RequiresPermission;
import com.app.billing.service.AttendanceReportService;
import com.app.billing.service.BiometricDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/biometric-devices")
@RequiredArgsConstructor
@Tag(name = "Biometric Devices", description = "Fingerprint device registration, employee mapping and raw punch ingestion")
public class BiometricDeviceController {

    private final BiometricDeviceService deviceService;
    private final AttendanceReportService reportService;

    @PostMapping("/register")
    @RequiresPermission(module = Modules.BIOMETRIC_DEVICES, action = Modules.CREATE)
    @Operation(summary = "Register Device", description = "Register a new fingerprint reader/device")
    public ResponseEntity<AttendanceDevice> registerDevice(@RequestBody AttendanceDevice device) {
        AttendanceDevice saved = deviceService.registerDevice(device);
        maskSensitiveDeviceData(saved);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/list")
    @RequiresPermission(module = Modules.BIOMETRIC_DEVICES, action = Modules.VIEW)
    @Operation(summary = "List Devices", description = "Get list of registered biometric devices")
    public ResponseEntity<List<AttendanceDevice>> listDevices() {
        List<AttendanceDevice> list = deviceService.getAllDevices();
        list.forEach(this::maskSensitiveDeviceData);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/test-connection/{deviceId}")
    @RequiresPermission(module = Modules.BIOMETRIC_DEVICES, action = Modules.VIEW)
    @Operation(summary = "Test Device Connection", description = "Test direct ISAPI connectivity with the biometric device")
    public ResponseEntity<com.app.billing.service.HikvisionIsapiClient.ConnectionTestResult> testConnection(@PathVariable String deviceId) {
        return ResponseEntity.ok(deviceService.testConnection(deviceId));
    }

    @PostMapping("/sync/{deviceId}")
    @RequiresPermission(module = Modules.BIOMETRIC_DEVICES, action = Modules.VIEW)
    @Operation(summary = "Sync Device Now", description = "Immediately fetch and process punch records from the device")
    public ResponseEntity<java.util.Map<String, Object>> syncNow(@PathVariable String deviceId) {
        int synced = deviceService.syncDeviceNow(deviceId);
        return ResponseEntity.ok(java.util.Map.of("success", true, "syncedCount", synced));
    }

    @PostMapping("/map-employee")
    @RequiresPermission(module = Modules.BIOMETRIC_DEVICES, action = "MAP_EMPLOYEE")
    @Operation(summary = "Map Employee to Device", description = "Map employee ID to fingerprint device user/enrollment ID")
    public ResponseEntity<DeviceEmployeeMapping> mapEmployee(@RequestBody MappingRequest request) {
        return ResponseEntity.ok(deviceService.mapEmployeeToDevice(
                request.getEmployeeId(),
                request.getDeviceUserId(),
                request.getDeviceId(),
                request.getCardNo()
        ));
    }

    @GetMapping("/mappings/{employeeId}")
    @RequiresPermission(module = Modules.BIOMETRIC_DEVICES, action = Modules.VIEW)
    @Operation(summary = "Employee Mappings", description = "Get device mappings for an employee")
    public ResponseEntity<List<DeviceEmployeeMapping>> getMappings(@PathVariable String employeeId) {
        return ResponseEntity.ok(deviceService.getMappingsForEmployee(employeeId));
    }

    @PostMapping(value = "/punches", consumes = {"application/json", "application/xml", "text/xml", "text/plain", "*/*"})
    @Operation(summary = "Ingest Device Punches", description = "Receive punch events from fingerprint device API or Hikvision HTTP listening push")
    public ResponseEntity<Void> ingestPunches(@RequestBody(required = false) String rawBody) {
        log.info("Received punch payload from device: {}", rawBody != null ? (rawBody.length() > 500 ? rawBody.substring(0, 500) + "..." : rawBody) : "null");
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(rawBody);

            // Case 1: Custom PunchBatchRequest format
            if (root.has("deviceId") && root.has("punches")) {
                PunchBatchRequest batch = mapper.treeToValue(root, PunchBatchRequest.class);
                deviceService.processRawPunches(batch.getDeviceId(), batch.getPunches());
                return ResponseEntity.ok().build();
            }

            // Case 2: Hikvision HTTP Listening Push format (AccessControllerEvent)
            com.fasterxml.jackson.databind.JsonNode acsEvent = root.path("AccessControllerEvent");
            if (acsEvent.isMissingNode()) {
                acsEvent = root.path("AcsEvent");
            }

            if (!acsEvent.isMissingNode() || root.has("employeeNoString") || root.has("employeeNo")) {
                com.fasterxml.jackson.databind.JsonNode targetNode = !acsEvent.isMissingNode() ? acsEvent : root;
                String employeeNo = targetNode.has("employeeNoString") ? targetNode.path("employeeNoString").asText() :
                                    (targetNode.has("employeeNo") ? targetNode.path("employeeNo").asText() : "");
                String timeStr = targetNode.path("time").asText();
                String deviceSerial = targetNode.path("deviceName").asText("");

                if (employeeNo != null && !employeeNo.isBlank() && timeStr != null && !timeStr.isBlank()) {
                    java.time.LocalDateTime punchTime;
                    if (timeStr.contains("+") || timeStr.endsWith("Z")) {
                        punchTime = java.time.OffsetDateTime.parse(timeStr).toLocalDateTime();
                    } else {
                        punchTime = java.time.LocalDateTime.parse(timeStr);
                    }

                    List<BiometricDeviceService.RawPunchRequest> punches = List.of(
                            new BiometricDeviceService.RawPunchRequest(employeeNo, punchTime, "IN", rawBody)
                    );
                    deviceService.processRawPunches(deviceSerial, punches);
                    log.info("Successfully processed Hikvision push punch for employee: {}", employeeNo);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse raw biometric punch payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void maskSensitiveDeviceData(AttendanceDevice device) {
        if (device != null && device.getPassword() != null && !device.getPassword().isBlank()) {
            device.setPassword("********");
        }
    }

    @GetMapping("/export/excel")
    @RequiresPermission(module = Modules.ATTENDANCE_DEVICE, action = Modules.EXPORT)
    @Operation(summary = "Export Biometric Devices Excel")
    public ResponseEntity<byte[]> exportExcel() throws java.io.IOException {
        byte[] excel = reportService.generateBiometricDeviceReportExcel();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Biometric_Devices.xlsx")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/export/pdf")
    @RequiresPermission(module = Modules.ATTENDANCE_DEVICE, action = Modules.EXPORT)
    @Operation(summary = "Export Biometric Devices PDF")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdf = reportService.generateBiometricDeviceReportPdf();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Biometric_Devices.pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Data
    public static class MappingRequest {
        private String employeeId;
        private String deviceUserId;
        private String deviceId;
        private String cardNo;
    }

    @Data
    public static class PunchBatchRequest {
        private String deviceId;
        private List<BiometricDeviceService.RawPunchRequest> punches;
    }
}

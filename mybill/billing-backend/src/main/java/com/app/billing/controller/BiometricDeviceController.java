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

    @PostMapping("/punches")
    @Operation(summary = "Ingest Device Punches", description = "Receive punch events from fingerprint device API/agent")
    public ResponseEntity<Void> ingestPunches(@RequestBody PunchBatchRequest batch) {
        log.info("Ingesting {} punches from device {}", batch.getPunches() != null ? batch.getPunches().size() : 0, batch.getDeviceId());
        deviceService.processRawPunches(batch.getDeviceId(), batch.getPunches());
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

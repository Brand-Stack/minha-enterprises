package com.app.billing.service;

import com.app.billing.dao.AttendanceDeviceRepository;
import com.app.billing.dao.AttendanceRecordRepository;
import com.app.billing.dao.DeviceEmployeeMappingRepository;
import com.app.billing.dao.EmployeeRepository;
import com.app.billing.dto.CompanySettingsDto;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.AttendanceDevice;
import com.app.billing.model.AttendancePunch;
import com.app.billing.model.AttendanceRecord;
import com.app.billing.model.DeviceEmployeeMapping;
import com.app.billing.model.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricDeviceServiceImpl implements BiometricDeviceService {

    private final AttendanceDeviceRepository deviceRepository;
    private final DeviceEmployeeMappingRepository mappingRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanySettingsService companySettingsService;
    private final AttendanceCalculationEngine calculationEngine;
    private final AuditService auditService;
    private final HikvisionIsapiClient isapiClient;

    @Override
    public AttendanceDevice registerDevice(AttendanceDevice device) {
        if (device.getDeviceId() == null || device.getDeviceId().isBlank()) {
            device.setDeviceId("DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (device.getStatus() == null) {
            device.setStatus(AttendanceDevice.DeviceStatus.ONLINE);
        }
        if (device.getUseHttps() == null) {
            device.setUseHttps(true);
        }
        if (device.getPort() == null || device.getPort() <= 0) {
            device.setPort(device.getUseHttps() ? 443 : 80);
        }

        // If updating an existing device and password was left blank, retain old password
        if (device.getId() != null && (device.getPassword() == null || device.getPassword().isBlank())) {
            deviceRepository.findById(device.getId()).ifPresent(old -> device.setPassword(old.getPassword()));
        }

        AttendanceDevice saved = deviceRepository.save(device);
        auditService.log("REGISTER_DEVICE", "ATTENDANCE_DEVICE", saved.getDeviceId(), "Registered biometric device " + saved.getDeviceName());
        return saved;
    }

    @Override
    public List<AttendanceDevice> getAllDevices() {
        return deviceRepository.findAll();
    }

    @Override
    public AttendanceDevice getDeviceById(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Biometric device not found with ID: " + deviceId));
    }

    @Override
    public DeviceEmployeeMapping mapEmployeeToDevice(String employeeId, String deviceUserId, String deviceId, String cardNo) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

        // Update employee record
        employee.setBiometricEnrollmentId(deviceUserId);
        employeeRepository.save(employee);

        Optional<DeviceEmployeeMapping> existing = mappingRepository.findByDeviceIdAndDeviceUserId(deviceId, deviceUserId);
        DeviceEmployeeMapping mapping;
        if (existing.isPresent()) {
            mapping = existing.get();
            mapping.setEmployeeId(employee.getId());
            mapping.setEmployeeCode(employee.getEmployeeCode());
            mapping.setEmployeeName(employee.getEmployeeName());
            mapping.setCardNo(cardNo);
            mapping.setActive(true);
        } else {
            mapping = DeviceEmployeeMapping.builder()
                    .employeeId(employee.getId())
                    .employeeCode(employee.getEmployeeCode())
                    .employeeName(employee.getEmployeeName())
                    .deviceId(deviceId)
                    .deviceUserId(deviceUserId)
                    .cardNo(cardNo)
                    .active(true)
                    .build();
        }
        DeviceEmployeeMapping saved = mappingRepository.save(mapping);
        auditService.log("MAP_EMPLOYEE_DEVICE", "ATTENDANCE_DEVICE", saved.getId(),
                "Mapped employee " + employee.getEmployeeName() + " to device user ID " + deviceUserId);
        return saved;
    }

    @Override
    public List<DeviceEmployeeMapping> getMappingsForEmployee(String employeeId) {
        return mappingRepository.findByEmployeeId(employeeId);
    }

    @Override
    public void processRawPunches(String deviceId, List<RawPunchRequest> rawPunches) {
        if (rawPunches == null || rawPunches.isEmpty()) {
            return;
        }

        CompanySettingsDto settings = companySettingsService.getSettings();
        Optional<AttendanceDevice> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isPresent()) {
            AttendanceDevice device = deviceOpt.get();
            device.setLastSyncTime(LocalDateTime.now());
            device.setLastSyncMessage("Synced " + rawPunches.size() + " punches at " + LocalDateTime.now());
            device.setStatus(AttendanceDevice.DeviceStatus.ONLINE);
            deviceRepository.save(device);
        }

        for (RawPunchRequest raw : rawPunches) {
            try {
                // Resolve Employee from Device Mapping or biometricEnrollmentId
                Optional<DeviceEmployeeMapping> mappingOpt = mappingRepository.findByDeviceIdAndDeviceUserId(deviceId, raw.deviceUserId());
                Employee employee = null;
                if (mappingOpt.isPresent()) {
                    employee = employeeRepository.findById(mappingOpt.get().getEmployeeId()).orElse(null);
                }
                if (employee == null) {
                    employee = employeeRepository.findAll().stream()
                            .filter(e -> raw.deviceUserId().equalsIgnoreCase(e.getBiometricEnrollmentId()))
                            .findFirst()
                            .orElse(null);
                }

                if (employee == null) {
                    log.warn("Unmapped fingerprint punch received for deviceUserId: {} on device: {}", raw.deviceUserId(), deviceId);
                    continue;
                }

                LocalDate date = raw.punchTimestamp().toLocalDate();
                AttendancePunch.PunchType pType = "OUT".equalsIgnoreCase(raw.punchType()) ? AttendancePunch.PunchType.OUT : AttendancePunch.PunchType.IN;

                AttendancePunch newPunch = AttendancePunch.builder()
                        .punchId("PUNCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .timestamp(raw.punchTimestamp())
                        .type(pType)
                        .source(AttendancePunch.PunchSource.FINGERPRINT_DEVICE)
                        .deviceId(deviceId)
                        .rawPayload(raw.rawPayload())
                        .processed(true)
                        .build();

                Optional<AttendanceRecord> recordOpt = attendanceRecordRepository.findByEmployeeIdAndDate(employee.getId(), date);
                AttendanceRecord record;
                List<AttendancePunch> punches = new ArrayList<>();

                if (recordOpt.isPresent()) {
                    record = recordOpt.get();
                    if (record.getPunches() != null) {
                        punches.addAll(record.getPunches());
                    }
                } else {
                    record = AttendanceRecord.builder()
                            .employeeId(employee.getId())
                            .employeeCode(employee.getEmployeeCode())
                            .employeeName(employee.getEmployeeName())
                            .department(employee.getCategory())
                            .date(date)
                            .build();
                }

                // Suppress duplicate punches (same timestamp within 1 minute)
                boolean isDuplicate = punches.stream().anyMatch(existingPunch ->
                        Math.abs(java.time.Duration.between(existingPunch.getTimestamp(), raw.punchTimestamp()).getSeconds()) < 60
                );

                if (isDuplicate) {
                    log.info("Suppressed duplicate punch for employee: {} at {}", employee.getEmployeeCode(), raw.punchTimestamp());
                    continue;
                }

                punches.add(newPunch);

                // Recalculate attendance record
                AttendanceRecord updatedRecord = calculationEngine.calculateDailyAttendance(
                        employee.getId(),
                        employee.getEmployeeCode(),
                        employee.getEmployeeName(),
                        employee.getCategory(),
                        date,
                        punches,
                        settings,
                        null,
                        null
                );

                if (record.getId() != null) {
                    updatedRecord.setId(record.getId());
                }

                attendanceRecordRepository.save(updatedRecord);

            } catch (Exception e) {
                log.error("Failed to process punch for deviceUserId {}: {}", raw.deviceUserId(), e.getMessage(), e);
            }
        }
    }

    @Override
    public HikvisionIsapiClient.ConnectionTestResult testConnection(String deviceId) {
        AttendanceDevice device = getDeviceById(deviceId);
        if (device.getDeviceIp() == null || device.getDeviceIp().isBlank()) {
            return new HikvisionIsapiClient.ConnectionTestResult(false, "ERROR", "Device IP is not configured", null, null, null);
        }

        int port = (device.getPort() != null && device.getPort() > 0) ? device.getPort() : (Boolean.TRUE.equals(device.getUseHttps()) ? 443 : 80);
        boolean useHttps = !Boolean.FALSE.equals(device.getUseHttps());
        String user = device.getUsername() != null ? device.getUsername() : "admin";
        String pass = device.getPassword() != null ? device.getPassword() : "";

        HikvisionIsapiClient.ConnectionTestResult result = isapiClient.testConnection(device.getDeviceIp(), port, useHttps, user, pass);
        if (result.success()) {
            device.setStatus(AttendanceDevice.DeviceStatus.ONLINE);
            device.setLastSyncMessage("Connection test succeeded. Model: " + result.model());
            if (result.model() != null && (device.getModel() == null || device.getModel().isBlank())) {
                device.setModel(result.model());
            }
            if (result.serialNumber() != null && (device.getSerialNumber() == null || device.getSerialNumber().isBlank())) {
                device.setSerialNumber(result.serialNumber());
            }
        } else {
            device.setStatus(AttendanceDevice.DeviceStatus.OFFLINE);
            device.setLastSyncMessage("Connection test failed: " + result.message());
        }
        deviceRepository.save(device);
        return result;
    }

    @Override
    public int syncDeviceNow(String deviceId) {
        AttendanceDevice device = getDeviceById(deviceId);
        if (device.getDeviceIp() == null || device.getDeviceIp().isBlank()) {
            log.warn("Cannot sync device {}: IP address not configured", deviceId);
            return 0;
        }

        int port = (device.getPort() != null && device.getPort() > 0) ? device.getPort() : (Boolean.TRUE.equals(device.getUseHttps()) ? 443 : 80);
        boolean useHttps = !Boolean.FALSE.equals(device.getUseHttps());
        String user = device.getUsername() != null ? device.getUsername() : "admin";
        String pass = device.getPassword() != null ? device.getPassword() : "";
        Long startSerialNo = device.getLastSyncSerialNo();

        List<HikvisionIsapiClient.HikvisionPunchEvent> events = isapiClient.fetchAccessEvents(
                device.getDeviceIp(), port, useHttps, user, pass, startSerialNo, 30
        );

        if (events.isEmpty()) {
            device.setLastSyncTime(LocalDateTime.now());
            device.setLastSyncMessage("Sync complete. 0 new punches found.");
            deviceRepository.save(device);
            return 0;
        }

        // Sort ascending by serialNo
        events.sort(java.util.Comparator.comparingLong(HikvisionIsapiClient.HikvisionPunchEvent::serialNo));

        List<RawPunchRequest> punches = new ArrayList<>();
        long maxSerial = startSerialNo != null ? startSerialNo : 0;

        for (HikvisionIsapiClient.HikvisionPunchEvent evt : events) {
            try {
                // Parse timestamp e.g. "2026-10-10T08:54:52+05:30"
                LocalDateTime punchTime;
                if (evt.timestamp().contains("+") || evt.timestamp().endsWith("Z")) {
                    punchTime = java.time.OffsetDateTime.parse(evt.timestamp()).toLocalDateTime();
                } else {
                    punchTime = LocalDateTime.parse(evt.timestamp());
                }

                punches.add(new RawPunchRequest(
                        evt.employeeNo(),
                        punchTime,
                        "IN", // System auto-determines IN/OUT based on daily attendance logic
                        evt.rawJson()
                ));

                if (evt.serialNo() > maxSerial) {
                    maxSerial = evt.serialNo();
                }
            } catch (Exception ex) {
                log.warn("Could not parse event timestamp {}: {}", evt.timestamp(), ex.getMessage());
            }
        }

        if (!punches.isEmpty()) {
            processRawPunches(deviceId, punches);
            device.setLastSyncSerialNo(maxSerial);
            device.setLastSyncTime(LocalDateTime.now());
            device.setLastSyncMessage("Successfully synced " + punches.size() + " punches at " + LocalDateTime.now());
            deviceRepository.save(device);
        }

        return punches.size();
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void syncAllConfiguredDevices() {
        List<AttendanceDevice> devices = deviceRepository.findAll();
        for (AttendanceDevice dev : devices) {
            if (dev.getDeviceIp() != null && !dev.getDeviceIp().isBlank()
                    && dev.getPassword() != null && !dev.getPassword().isBlank()) {
                try {
                    syncDeviceNow(dev.getDeviceId());
                } catch (Exception e) {
                    log.error("Background sync failed for device {}: {}", dev.getDeviceId(), e.getMessage());
                }
            }
        }
    }
}

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

    @Override
    public AttendanceDevice registerDevice(AttendanceDevice device) {
        if (device.getDeviceId() == null || device.getDeviceId().isBlank()) {
            device.setDeviceId("DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (device.getStatus() == null) {
            device.setStatus(AttendanceDevice.DeviceStatus.ONLINE);
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
}

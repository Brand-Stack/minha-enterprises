package com.app.billing.service;

import com.app.billing.model.AttendanceDevice;
import com.app.billing.model.DeviceEmployeeMapping;

import java.time.LocalDateTime;
import java.util.List;

public interface BiometricDeviceService {
    AttendanceDevice registerDevice(AttendanceDevice device);
    List<AttendanceDevice> getAllDevices();
    AttendanceDevice getDeviceById(String deviceId);
    
    DeviceEmployeeMapping mapEmployeeToDevice(String employeeId, String deviceUserId, String deviceId, String cardNo);
    List<DeviceEmployeeMapping> getMappingsForEmployee(String employeeId);
    
    void processRawPunches(String deviceId, List<RawPunchRequest> rawPunches);

    public record RawPunchRequest(
            String deviceUserId,
            LocalDateTime punchTimestamp,
            String punchType, // IN or OUT
            String rawPayload
    ) {}
}

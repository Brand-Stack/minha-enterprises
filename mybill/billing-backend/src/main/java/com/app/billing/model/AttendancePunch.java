package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePunch {
    private String punchId;
    private LocalDateTime timestamp;
    private PunchType type; // IN, OUT
    private PunchSource source; // MANUAL, FINGERPRINT_DEVICE, MOBILE_APP, SYSTEM_AUTO
    private String deviceId;
    private String rawPayload;
    private Boolean processed;

    // Location & Audit details
    private Double latitude;
    private Double longitude;
    private Double distanceFromOfficeMeters;
    private Boolean punchedByAdmin;
    private String adminEmployeeId;

    public enum PunchType {
        IN, OUT
    }

    public enum PunchSource {
        MANUAL, FINGERPRINT_DEVICE, MOBILE_APP, SYSTEM_AUTO
    }
}

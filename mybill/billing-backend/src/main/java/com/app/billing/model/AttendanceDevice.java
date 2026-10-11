package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "attendance_devices")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AttendanceDevice extends BaseEntity {
    @Indexed(unique = true)
    private String deviceId;
    private String deviceName;
    private String deviceIp;
    private Integer port;
    private String serialNumber;
    private String location;
    private String model;
    private String username;
    private String password;
    private Boolean useHttps;
    private Long lastSyncSerialNo;
    private DeviceStatus status;
    private LocalDateTime lastSyncTime;
    private String lastSyncMessage;
    private Integer syncIntervalMinutes;

    public enum DeviceStatus {
        ONLINE,
        OFFLINE,
        SYNCING,
        ERROR
    }
}

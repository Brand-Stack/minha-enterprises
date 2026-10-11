package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "device_employee_mappings")
@CompoundIndex(name = "dev_user_idx", def = "{'deviceId': 1, 'deviceUserId': 1}", unique = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DeviceEmployeeMapping extends BaseEntity {
    @Indexed
    private String employeeId;
    private String employeeCode;
    private String employeeName;

    private String deviceId;
    @Indexed
    private String deviceUserId; // enrollment / user ID on the fingerprint reader
    private String cardNo;
    private Boolean active;
}

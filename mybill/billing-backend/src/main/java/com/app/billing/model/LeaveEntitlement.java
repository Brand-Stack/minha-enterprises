package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "leave_entitlements")
@CompoundIndex(name = "emp_year_idx", def = "{'employeeId': 1, 'year': 1}", unique = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeaveEntitlement extends BaseEntity {
    private String employeeId;
    private String employeeCode;
    private String employeeName;
    private Integer year;

    @Builder.Default
    private Integer casualLeaveTotal = 12;
    @Builder.Default
    private Integer casualLeaveUsed = 0;

    @Builder.Default
    private Integer medicalLeaveTotal = 10;
    @Builder.Default
    private Integer medicalLeaveUsed = 0;

    @Builder.Default
    private Integer emergencyLeaveTotal = 5;
    @Builder.Default
    private Integer emergencyLeaveUsed = 0;

    @Builder.Default
    private Integer compOffTotal = 3;
    @Builder.Default
    private Integer compOffUsed = 0;

    @Builder.Default
    private Integer otherLeaveTotal = 0;
    @Builder.Default
    private Integer otherLeaveUsed = 0;

    @Builder.Default
    private Integer carryForwardDays = 0;

    public int getCasualLeaveRemaining() {
        return Math.max(0, (casualLeaveTotal != null ? casualLeaveTotal : 0) - (casualLeaveUsed != null ? casualLeaveUsed : 0));
    }

    public int getMedicalLeaveRemaining() {
        return Math.max(0, (medicalLeaveTotal != null ? medicalLeaveTotal : 0) - (medicalLeaveUsed != null ? medicalLeaveUsed : 0));
    }

    public int getEmergencyLeaveRemaining() {
        return Math.max(0, (emergencyLeaveTotal != null ? emergencyLeaveTotal : 0) - (emergencyLeaveUsed != null ? emergencyLeaveUsed : 0));
    }

    public int getCompOffRemaining() {
        return Math.max(0, (compOffTotal != null ? compOffTotal : 0) - (compOffUsed != null ? compOffUsed : 0));
    }

    public int getOtherLeaveRemaining() {
        return Math.max(0, (otherLeaveTotal != null ? otherLeaveTotal : 0) - (otherLeaveUsed != null ? otherLeaveUsed : 0));
    }
}

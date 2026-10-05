package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Append-only audit record for entitlement-related and security-sensitive actions
 * (login, logout, CRUD, export, print, email, password reset, permission changes).
 */
@Document(collection = "audit_logs")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuditLog extends BaseEntity {

    private String employeeId;
    private String username;
    private String role;
    private String category;

    /** Action performed: LOGIN, LOGOUT, CREATE, UPDATE, DELETE, EXPORT, PRINT, EMAIL, PASSWORD_RESET, PERMISSION_CHANGE. */
    private String action;

    /** Module key the action targeted (may be null for auth events). */
    private String module;

    /** Optional id of the affected entity / category. */
    private String targetId;

    /** Free-form description for additional context. */
    private String details;

    private String ipAddress;

    @Indexed
    private LocalDateTime timestamp;
}

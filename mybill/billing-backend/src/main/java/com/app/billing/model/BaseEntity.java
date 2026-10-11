package com.app.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@Document
public abstract class BaseEntity {
    @Id
    private String id;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private String createdBy; // Username/email of user who created the record
    private String updatedBy; // Username/email of user who last updated the record
    private String lastUpdatedBy; // Alias for updatedBy - tracks who last modified the record
}


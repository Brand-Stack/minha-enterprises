package com.app.billing.dao;

import com.app.billing.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    long countByRecipientEmployeeIdAndReadFalse(String recipientEmployeeId);

    List<Notification> findByRecipientEmployeeIdOrderByCreatedAtDesc(String recipientEmployeeId, Pageable pageable);

    List<Notification> findByRecipientEmployeeIdAndReadOrderByCreatedAtDesc(String recipientEmployeeId, boolean read, Pageable pageable);

    List<Notification> findByRecipientEmployeeIdAndModuleOrderByCreatedAtDesc(String recipientEmployeeId, String module, Pageable pageable);

    List<Notification> findByRecipientEmployeeIdAndReadAndModuleOrderByCreatedAtDesc(String recipientEmployeeId, boolean read, String module, Pageable pageable);
}

package com.schwab.notificationservice.repository;

import com.schwab.notificationservice.domain.AuditEvent;
import com.schwab.notificationservice.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByNotification(Notification notification);
}

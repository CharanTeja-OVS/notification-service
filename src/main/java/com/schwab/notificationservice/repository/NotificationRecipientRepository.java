package com.schwab.notificationservice.repository;

import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, UUID> {
    List<NotificationRecipient> findByNotification(Notification notification);
}

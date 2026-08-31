package com.schwab.notificationservice.repository;

import com.schwab.notificationservice.domain.DeliveryAttempt;
import com.schwab.notificationservice.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findByNotification(Notification notification);
}

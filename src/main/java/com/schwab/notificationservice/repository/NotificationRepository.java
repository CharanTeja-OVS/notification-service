package com.schwab.notificationservice.repository;

import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Override
    @EntityGraph(attributePaths = {"recipients"})
    Optional<Notification> findById(UUID id);

    @EntityGraph(attributePaths = {"recipients"})
    Optional<Notification> findByNotificationId(String notificationId);

    @EntityGraph(attributePaths = {"recipients"})
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"recipients"})
    List<Notification> findByStatusIn(Collection<NotificationStatus> statuses);
}

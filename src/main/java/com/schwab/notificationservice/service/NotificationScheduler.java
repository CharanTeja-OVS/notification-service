package com.schwab.notificationservice.service;

import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationSeverity;
import com.schwab.notificationservice.domain.NotificationStatus;
import com.schwab.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationRepository notificationRepository;
    private final DeliveryProcessorService deliveryProcessorService;

    public NotificationScheduler(NotificationRepository notificationRepository,
                                DeliveryProcessorService deliveryProcessorService) {
        this.notificationRepository = notificationRepository;
        this.deliveryProcessorService = deliveryProcessorService;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processPendingNotifications() {
        List<Notification> pending = notificationRepository.findByStatusIn(List.of(NotificationStatus.ACCEPTED, NotificationStatus.ROUTED));

        if (pending.isEmpty()) {
            return;
        }

        pending.stream()
                .filter(notification -> !isUrgent(notification.getSeverity()))
                .filter(this::isDueForProcessing)
                .sorted(Comparator.comparingInt(this::severityPriority).reversed())
                .forEach(notification -> {
                    log.info("Scheduling delayed processing for notification {} with severity {}",
                            notification.getNotificationId(), notification.getSeverity());
                    deliveryProcessorService.process(notification);
                });
    }

    @Scheduled(fixedDelay = 20000)
    @Transactional
    public void processQueuedNotifications() {
        List<Notification> queued = notificationRepository.findByStatusIn(List.of(NotificationStatus.QUEUED));

        if (queued.isEmpty()) {
            return;
        }

        queued.stream()
                .sorted(Comparator.comparingInt(this::severityPriority).reversed())
                .forEach(notification -> {
                    log.info("Finalizing queued notification {} with severity {}",
                            notification.getNotificationId(), notification.getSeverity());
                    notification.setStatus(NotificationStatus.DELIVERED);
                    notificationRepository.save(notification);
                });
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> failed = notificationRepository.findByStatusIn(List.of(NotificationStatus.FAILED, NotificationStatus.PROCESSING));

        if (failed.isEmpty()) {
            return;
        }

        failed.stream()
                .filter(this::isDueForProcessing)
                .sorted(Comparator.comparingInt(this::severityPriority).reversed())
                .forEach(notification -> {
                    log.info("Retrying failed notification {} with severity {}",
                            notification.getNotificationId(), notification.getSeverity());
                    notification.setStatus(NotificationStatus.ROUTED);
                    notificationRepository.save(notification);
                    deliveryProcessorService.process(notification);
                });
    }

    private boolean isUrgent(NotificationSeverity severity) {
        return severity == NotificationSeverity.CRITICAL || severity == NotificationSeverity.HIGH;
    }

    private boolean isDueForProcessing(Notification notification) {
        if (notification == null) {
            return false;
        }
        Instant scheduledAt = notification.getScheduledAt();
        return scheduledAt == null || !scheduledAt.isAfter(Instant.now());
    }

    private int severityPriority(Notification notification) {
        if (notification == null || notification.getSeverity() == null) {
            return 0;
        }
        return switch (notification.getSeverity()) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }
}

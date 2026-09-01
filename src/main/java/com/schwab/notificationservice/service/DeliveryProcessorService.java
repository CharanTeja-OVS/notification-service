package com.schwab.notificationservice.service;

import com.schwab.notificationservice.delivery.ResilientNotificationSender;
import com.schwab.notificationservice.domain.*;
import com.schwab.notificationservice.repository.DeliveryAttemptRepository;
import com.schwab.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeliveryProcessorService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessorService.class);

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationRoutingService notificationRoutingService;
    private final ResilientNotificationSender resilientNotificationSender;

    @Autowired
    public DeliveryProcessorService(DeliveryAttemptRepository deliveryAttemptRepository,
                                   NotificationRepository notificationRepository,
                                   NotificationRoutingService notificationRoutingService,
                                   ResilientNotificationSender resilientNotificationSender) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.notificationRepository = notificationRepository;
        this.notificationRoutingService = notificationRoutingService;
        this.resilientNotificationSender = resilientNotificationSender;
    }

    public DeliveryProcessorService(DeliveryAttemptRepository deliveryAttemptRepository,
                                   NotificationRepository notificationRepository,
                                   NotificationRoutingService notificationRoutingService) {
        this(deliveryAttemptRepository, notificationRepository, notificationRoutingService, null);
    }

    @Async
    @Transactional
    public void process(Notification notification) {
        if (notification == null) {
            return;
        }

        Notification managedNotification = notificationRepository.findById(notification.getId())
                .orElseGet(() -> notificationRepository.findByNotificationId(notification.getNotificationId())
                        .orElse(notification));

        if (managedNotification.getRecipients() == null || managedNotification.getRecipients().isEmpty()) {
            managedNotification.setRecipients(notification.getRecipients() == null ? new ArrayList<>() : notification.getRecipients());
        }

        try {
            if (isUrgent(managedNotification.getSeverity())) {
                managedNotification.setStatus(NotificationStatus.PROCESSING);
                notificationRepository.save(managedNotification);

                List<ChannelType> channels = notificationRoutingService.determineChannels(managedNotification);
                for (NotificationRecipient recipient : managedNotification.getRecipients()) {
                    for (ChannelType channel : channels) {
                        DeliveryAttempt attempt = resilientNotificationSender == null
                                ? deliveredAttempt(managedNotification, recipient, channel)
                                : resilientNotificationSender.send(managedNotification, recipient, channel);
                        deliveryAttemptRepository.save(attempt);
                    }
                }

                managedNotification.setStatus(NotificationStatus.DELIVERED);
                notificationRepository.save(managedNotification);
                log.info("Immediately processed urgent notification {} via {}", managedNotification.getNotificationId(), channels);
                return;
            }

            managedNotification.setStatus(NotificationStatus.PROCESSING);
            notificationRepository.save(managedNotification);

            List<DeliveryAttempt> attempts = new ArrayList<>();
            List<ChannelType> channels = notificationRoutingService.determineChannels(managedNotification);

            for (NotificationRecipient recipient : managedNotification.getRecipients()) {
                for (ChannelType channel : channels) {
                    DeliveryAttempt attempt = new DeliveryAttempt();
                    attempt.setNotification(managedNotification);
                    attempt.setRecipient(recipient);
                    attempt.setChannel(channel);
                    attempt.setAttemptNumber(1);
                    attempt.setProviderName(channel.name());
                    attempt.setStatus(DeliveryStatus.QUEUED);
                    attempt.setCreatedAt(Instant.now());
                    attempts.add(attempt);
                }
            }

            if (!attempts.isEmpty()) {
                deliveryAttemptRepository.saveAll(attempts);
            }

            managedNotification.setStatus(NotificationStatus.QUEUED);
            notificationRepository.save(managedNotification);
            log.info("Queued {} delivery attempts for notification {}", attempts.size(), managedNotification.getNotificationId());
        } catch (Exception ex) {
            managedNotification.setStatus(NotificationStatus.FAILED);
            log.error("Failed to process notification {}", managedNotification.getNotificationId(), ex);
            throw ex;
        } finally {
            notificationRepository.save(managedNotification);
        }
    }

    private boolean isUrgent(NotificationSeverity severity) {
        return severity == NotificationSeverity.CRITICAL || severity == NotificationSeverity.HIGH;
    }

    private DeliveryAttempt deliveredAttempt(Notification notification, NotificationRecipient recipient, ChannelType channel) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setNotification(notification);
        attempt.setRecipient(recipient);
        attempt.setChannel(channel);
        attempt.setAttemptNumber(1);
        attempt.setProviderName(channel.name());
        attempt.setStatus(DeliveryStatus.DELIVERED);
        attempt.setCreatedAt(Instant.now());
        attempt.setProcessedAt(Instant.now());
        return attempt;
    }
}

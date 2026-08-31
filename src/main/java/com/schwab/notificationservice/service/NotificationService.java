package com.schwab.notificationservice.service;

import com.schwab.notificationservice.api.NotificationRequest;
import com.schwab.notificationservice.api.NotificationResponse;
import com.schwab.notificationservice.delivery.ResilientNotificationSender;
import com.schwab.notificationservice.domain.*;
import com.schwab.notificationservice.repository.AuditEventRepository;
import com.schwab.notificationservice.repository.DeliveryAttemptRepository;
import com.schwab.notificationservice.repository.NotificationRecipientRepository;
import com.schwab.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationRoutingService routingService;
    private final DeliveryProcessorService deliveryProcessorService;
    private final ResilientNotificationSender resilientNotificationSender;

    public NotificationService(NotificationRepository notificationRepository,
                              NotificationRecipientRepository recipientRepository,
                              DeliveryAttemptRepository deliveryAttemptRepository,
                              AuditEventRepository auditEventRepository,
                              NotificationRoutingService routingService,
                              DeliveryProcessorService deliveryProcessorService,
                              ResilientNotificationSender resilientNotificationSender) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.auditEventRepository = auditEventRepository;
        this.routingService = routingService;
        this.deliveryProcessorService = deliveryProcessorService;
        this.resilientNotificationSender = resilientNotificationSender;
    }

    @Transactional
    public NotificationResponse submit(NotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Notification request is required");
        }
        if (request.getRecipients() == null) {
            throw new IllegalArgumentException("Recipients are required");
        }

        String resolvedNotificationId = resolveNotificationId(request.getNotificationId());
        String resolvedIdempotencyKey = resolveIdempotencyKey(request.getIdempotencyKey());

        Optional<Notification> existing = notificationRepository.findByIdempotencyKey(resolvedIdempotencyKey);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Duplicate idempotency key: " + resolvedIdempotencyKey);
        }

        Notification notification = new Notification();
        notification.setNotificationId(resolvedNotificationId);
        notification.setSourceSystem(request.getSourceSystem());
        notification.setCorrelationId(request.getCorrelationId());
        notification.setNotificationType(request.getNotificationType());
        notification.setSeverity(request.getSeverity());
        notification.setPriority(request.getPriority());
        notification.setIdempotencyKey(resolvedIdempotencyKey);
        notification.setScheduledAt(request.getScheduledAt());
        notification.setExpiresAt(request.getExpiresAt());
        notification.setStatus(NotificationStatus.ACCEPTED);

        List<NotificationRecipient> recipients = new ArrayList<>();
        for (NotificationRequest.RecipientRequest recipientRequest : request.getRecipients()) {
            NotificationRecipient recipient = new NotificationRecipient();
            recipient.setNotification(notification);
            recipient.setRecipientId(recipientRequest.getRecipientId());
            recipient.setContactValue(recipientRequest.getContactValue());
            if (recipientRequest.getPreferredChannels() != null && !recipientRequest.getPreferredChannels().isEmpty()) {
                recipient.setPreferredChannels(new java.util.HashSet<>(recipientRequest.getPreferredChannels()));
            } else {
                recipient.setPreferredChannels(new java.util.HashSet<>(List.of(ChannelType.EMAIL)));
            }
            recipients.add(recipient);
        }
        notification.setRecipients(recipients);

        notification = notificationRepository.save(notification);
        if (!recipients.isEmpty()) {
            recipientRepository.saveAll(recipients);
        }

        List<ChannelType> selectedChannels = routingService.determineChannels(notification);
        notification.setStatus(NotificationStatus.ROUTED);

        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setNotification(notification);
        auditEvent.setEventType(AuditEventType.ROUTING_DECISION);
        auditEvent.setDetails("Selected channels: " + selectedChannels);
        auditEventRepository.save(auditEvent);

        notification = notificationRepository.save(notification);

        if (isUrgent(notification.getSeverity())) {
            dispatchUrgentAfterCommit(notification);
        }

        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getStatus(),
                notification.getIdempotencyKey(),
                notification.getCreatedAt(),
                selectedChannels.stream().map(Enum::name).collect(Collectors.toList())
        );
    }

    private boolean isUrgent(NotificationSeverity severity) {
        if (severity == null) {
            return false;
        }
        return severity == NotificationSeverity.CRITICAL || severity == NotificationSeverity.HIGH;
    }

    private void dispatchUrgentAfterCommit(Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliveryProcessorService.process(notification);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliveryProcessorService.process(notification);
            }
        });
    }

    private String resolveNotificationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }

    private String resolveIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }

    public Optional<Notification> getByNotificationId(String notificationId) {
        return notificationRepository.findByNotificationId(notificationId);
    }

    public Optional<Notification> getByIdempotencyKey(String idempotencyKey) {
        return notificationRepository.findByIdempotencyKey(idempotencyKey);
    }

    public NotificationResponse getStatus(String notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        List<ChannelType> selected = routingService.determineChannels(notification);
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getStatus(),
                notification.getIdempotencyKey(),
                notification.getCreatedAt(),
                selected.stream().map(Enum::name).collect(Collectors.toList())
        );
    }

    public List<DeliveryAttempt> getDeliveryAttempts(String notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        return deliveryAttemptRepository.findByNotification(notification);
    }
}

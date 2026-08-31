package com.schwab.notificationservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_notifications_status", columnList = "status")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_uuid")
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private String notificationId;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "correlation_id")
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationRecipient> recipients = new ArrayList<>();

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryAttempt> deliveryAttempts = new ArrayList<>();

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditEvent> auditEvents = new ArrayList<>();

    public Notification() {
        this.createdAt = Instant.now();
        this.status = NotificationStatus.ACCEPTED;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(NotificationSeverity severity) {
        this.severity = severity;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public void setPriority(NotificationPriority priority) {
        this.priority = priority;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public List<NotificationRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<NotificationRecipient> recipients) {
        this.recipients = recipients;
    }

    public List<DeliveryAttempt> getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(List<DeliveryAttempt> deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    public List<AuditEvent> getAuditEvents() {
        return auditEvents;
    }

    public void setAuditEvents(List<AuditEvent> auditEvents) {
        this.auditEvents = auditEvents;
    }
}

package com.schwab.notificationservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private NotificationRecipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "failure_category")
    @Enumerated(EnumType.STRING)
    private FailureCategory failureCategory;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public DeliveryAttempt() {
        this.createdAt = Instant.now();
        this.status = DeliveryStatus.QUEUED;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public NotificationRecipient getRecipient() {
        return recipient;
    }

    public void setRecipient(NotificationRecipient recipient) {
        this.recipient = recipient;
    }

    public ChannelType getChannel() {
        return channel;
    }

    public void setChannel(ChannelType channel) {
        this.channel = channel;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public FailureCategory getFailureCategory() {
        return failureCategory;
    }

    public void setFailureCategory(FailureCategory failureCategory) {
        this.failureCategory = failureCategory;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}

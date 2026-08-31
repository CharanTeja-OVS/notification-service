package com.schwab.notificationservice.api;

import com.schwab.notificationservice.domain.ChannelType;
import com.schwab.notificationservice.domain.NotificationPriority;
import com.schwab.notificationservice.domain.NotificationSeverity;
import com.schwab.notificationservice.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class NotificationRequest {

    @NotBlank
    @Size(min = 3, max = 128)
    private String notificationId;

    @NotBlank
    @Size(min = 2, max = 64)
    private String sourceSystem;

    private String correlationId;

    @NotNull
    private NotificationType notificationType;

    @NotNull
    private NotificationSeverity severity;

    @NotNull
    private NotificationPriority priority;

    @NotEmpty
    private List<RecipientRequest> recipients;

    private List<ChannelType> requestedChannels;

    @NotBlank
    @Size(min = 3, max = 128)
    private String idempotencyKey;

    private Instant scheduledAt;
    private Instant expiresAt;

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

    public List<RecipientRequest> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<RecipientRequest> recipients) {
        this.recipients = recipients;
    }

    public List<ChannelType> getRequestedChannels() {
        return requestedChannels;
    }

    public void setRequestedChannels(List<ChannelType> requestedChannels) {
        this.requestedChannels = requestedChannels;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public static class RecipientRequest {
        @NotBlank
        @Size(min = 3, max = 128)
        private String recipientId;

        @NotBlank
        @Size(min = 3, max = 256)
        private String contactValue;

        private List<ChannelType> preferredChannels;

        public String getRecipientId() {
            return recipientId;
        }

        public void setRecipientId(String recipientId) {
            this.recipientId = recipientId;
        }

        public String getContactValue() {
            return contactValue;
        }

        public void setContactValue(String contactValue) {
            this.contactValue = contactValue;
        }

        public List<ChannelType> getPreferredChannels() {
            return preferredChannels;
        }

        public void setPreferredChannels(List<ChannelType> preferredChannels) {
            this.preferredChannels = preferredChannels;
        }
    }
}

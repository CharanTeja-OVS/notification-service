package com.schwab.notificationservice.api;

import com.schwab.notificationservice.domain.NotificationStatus;

import java.time.Instant;
import java.util.List;

public class NotificationResponse {
    private String notificationId;
    private String status;
    private String idempotencyKey;
    private Instant createdAt;
    private List<String> selectedChannels;

    public NotificationResponse() {
    }

    public NotificationResponse(String notificationId, NotificationStatus status, String idempotencyKey, Instant createdAt, List<String> selectedChannels) {
        this.notificationId = notificationId;
        this.status = status.name();
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.selectedChannels = selectedChannels;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public List<String> getSelectedChannels() {
        return selectedChannels;
    }

    public void setSelectedChannels(List<String> selectedChannels) {
        this.selectedChannels = selectedChannels;
    }
}

package com.schwab.notificationservice.domain;

public enum NotificationStatus {
    ACCEPTED,
    ROUTED,
    QUEUED,
    PROCESSING,
    PARTIALLY_DELIVERED,
    DELIVERED,
    FAILED,
    DUPLICATE_SUPPRESSED
}

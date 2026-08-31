package com.schwab.notificationservice.domain;

public enum DeliveryStatus {
    QUEUED,
    ATTEMPTED,
    DELIVERED,
    FAILED,
    RETRY_SCHEDULED,
    REJECTED
}

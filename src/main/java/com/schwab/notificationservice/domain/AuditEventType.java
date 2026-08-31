package com.schwab.notificationservice.domain;

public enum AuditEventType {
    ACCEPTED,
    REJECTED,
    ROUTING_DECISION,
    DELIVERY_QUEUED,
    DELIVERY_ATTEMPTED,
    DELIVERY_SUCCEEDED,
    DELIVERY_FAILED,
    RETRY_SCHEDULED,
    DUPLICATE_SUPPRESSED
}

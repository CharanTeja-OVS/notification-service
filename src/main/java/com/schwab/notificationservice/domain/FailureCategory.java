package com.schwab.notificationservice.domain;

public enum FailureCategory {
    TRANSIENT_PROVIDER_FAILURE,
    PERMANENT_PROVIDER_REJECTION,
    INVALID_RECIPIENT,
    RATE_LIMITED,
    TIMEOUT,
    AUTHENTICATION_ERROR
}

package com.schwab.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.delivery")
public class NotificationDeliveryProperties {

    private String mode = "kafka";
    private String topic = "notification-events";
    private int maxAttempts = 3;
    private long retryDelayMs = 250L;
    private boolean circuitBreakerEnabled = true;
    private int circuitBreakerFailureRateThreshold = 50;
    private long circuitBreakerWaitDurationMs = 30000L;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public int getCircuitBreakerFailureRateThreshold() {
        return circuitBreakerFailureRateThreshold;
    }

    public void setCircuitBreakerFailureRateThreshold(int circuitBreakerFailureRateThreshold) {
        this.circuitBreakerFailureRateThreshold = circuitBreakerFailureRateThreshold;
    }

    public long getCircuitBreakerWaitDurationMs() {
        return circuitBreakerWaitDurationMs;
    }

    public void setCircuitBreakerWaitDurationMs(long circuitBreakerWaitDurationMs) {
        this.circuitBreakerWaitDurationMs = circuitBreakerWaitDurationMs;
    }
}

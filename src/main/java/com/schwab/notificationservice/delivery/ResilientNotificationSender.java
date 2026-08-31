package com.schwab.notificationservice.delivery;

import com.schwab.notificationservice.domain.ChannelType;
import com.schwab.notificationservice.domain.DeliveryAttempt;
import com.schwab.notificationservice.domain.DeliveryStatus;
import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ResilientNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ResilientNotificationSender.class);

    private final NotificationRateLimiter rateLimiter;
    private final NotificationDeliveryPublisher publisher;
    private final int maxAttempts;
    private final long retryDelayMs;

    public ResilientNotificationSender(NotificationRateLimiter rateLimiter,
                                      NotificationDeliveryPublisher publisher,
                                      int maxAttempts,
                                      long retryDelayMs) {
        this.rateLimiter = rateLimiter;
        this.publisher = publisher;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    public DeliveryAttempt send(Notification notification, NotificationRecipient recipient, ChannelType channel) {
        if (notification == null || recipient == null || channel == null) {
            throw new IllegalArgumentException("notification, recipient and channel are required");
        }

        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            throw new RuntimeException("Rate limit exceeded for notification delivery");
        }

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setNotification(notification);
        attempt.setRecipient(recipient);
        attempt.setChannel(channel);
        attempt.setAttemptNumber(1);
        attempt.setProviderName(channel.name());
        attempt.setCreatedAt(Instant.now());

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            try {
                publisher.publish(notification, recipient, channel);
                attempt.setStatus(DeliveryStatus.DELIVERED);
                attempt.setAttemptNumber(attemptNumber);
                attempt.setProcessedAt(Instant.now());
                return attempt;
            } catch (Exception ex) {
                log.warn("Delivery attempt {} failed for notification {} via {}: {}",
                        attemptNumber, notification.getNotificationId(), channel, ex.getMessage());

                if (attemptNumber == maxAttempts) {
                    attempt.setStatus(DeliveryStatus.FAILED);
                    attempt.setProcessedAt(Instant.now());
                    throw new RuntimeException("Delivery failed after retries", ex);
                }

                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying delivery", interrupted);
                }
            }
        }

        attempt.setStatus(DeliveryStatus.QUEUED);
        attempt.setProcessedAt(Instant.now());
        return attempt;
    }
}

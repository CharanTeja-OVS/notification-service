package com.schwab.notificationservice;

import com.schwab.notificationservice.config.ChannelProperties;
import com.schwab.notificationservice.delivery.MessageNotificationPublisher;
import com.schwab.notificationservice.delivery.NotificationDeliveryPublisher;
import com.schwab.notificationservice.delivery.NotificationRateLimiter;
import com.schwab.notificationservice.delivery.ResilientNotificationSender;
import com.schwab.notificationservice.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResilientNotificationSenderTest {

    @Test
    void publishesAfterPermitsAreAvailable() {
        Notification notification = new Notification();
        notification.setNotificationId("n-1");
        notification.setIdempotencyKey("k-1");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-1");
        recipient.setContactValue("user@example.com");
        recipient.setPreferredChannels(java.util.Set.of(ChannelType.EMAIL));

        NotificationRateLimiter limiter = new NotificationRateLimiter(2, Duration.ofMinutes(1));
        TestPublisher publisher = new TestPublisher();
        ResilientNotificationSender sender = new ResilientNotificationSender(limiter, publisher, 2, 1);

        DeliveryAttempt attempt = sender.send(notification, recipient, ChannelType.EMAIL);

        assertEquals(DeliveryStatus.DELIVERED, attempt.getStatus());
        assertEquals(ChannelType.EMAIL, attempt.getChannel());
        assertTrue(publisher.sent);
    }

    @Test
    void rateLimitThrowsWhenQuotaIsExhausted() {
        Notification notification = new Notification();
        notification.setNotificationId("n-2");
        notification.setIdempotencyKey("k-2");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-2");
        recipient.setContactValue("user@example.com");
        recipient.setPreferredChannels(java.util.Set.of(ChannelType.EMAIL));

        NotificationRateLimiter limiter = new NotificationRateLimiter(0, Duration.ofMinutes(1));
        TestPublisher publisher = new TestPublisher();
        ResilientNotificationSender sender = new ResilientNotificationSender(limiter, publisher, 1, 0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sender.send(notification, recipient, ChannelType.EMAIL));

        assertTrue(ex.getMessage().contains("rate limit") || ex.getMessage().contains("Rate limit"));
    }

    @Test
    void rejectsNullInputs() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(10, Duration.ofMinutes(1));
        TestPublisher publisher = new TestPublisher();
        ResilientNotificationSender sender = new ResilientNotificationSender(limiter, publisher, 2, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sender.send(null, null, ChannelType.EMAIL));
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void retriesTransientFailureAndThenSucceeds() throws Exception {
        Notification notification = new Notification();
        notification.setNotificationId("n-3");
        notification.setIdempotencyKey("k-3");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-3");
        recipient.setContactValue("user@example.com");
        recipient.setPreferredChannels(java.util.Set.of(ChannelType.EMAIL));

        FlakyPublisher publisher = new FlakyPublisher(1);
        ResilientNotificationSender sender = new ResilientNotificationSender(new NotificationRateLimiter(10, Duration.ofMinutes(1)), publisher, 3, 0);

        DeliveryAttempt attempt = sender.send(notification, recipient, ChannelType.EMAIL);

        assertEquals(DeliveryStatus.DELIVERED, attempt.getStatus());
        assertEquals(2, publisher.attempts);
    }

    @Test
    void failsAfterAllRetriesAreExhausted() {
        Notification notification = new Notification();
        notification.setNotificationId("n-4");
        notification.setIdempotencyKey("k-4");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-4");
        recipient.setContactValue("user@example.com");
        recipient.setPreferredChannels(java.util.Set.of(ChannelType.EMAIL));

        AlwaysFailingPublisher publisher = new AlwaysFailingPublisher();
        ResilientNotificationSender sender = new ResilientNotificationSender(new NotificationRateLimiter(10, Duration.ofMinutes(1)), publisher, 2, 0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sender.send(notification, recipient, ChannelType.EMAIL));

        assertTrue(ex.getMessage().contains("Delivery failed after retries"));
        assertEquals(2, publisher.attempts);
    }

    @Test
    void rateLimitsAreIsolatedBySourceSystemAndChannel() {
        ChannelProperties.ChannelConfig portalEmail = enabledChannel(1);
        ChannelProperties.ChannelConfig mobileEmail = enabledChannel(1);
        ChannelProperties.ChannelConfig portalSms = enabledChannel(1);
        ChannelProperties.SourceSystemConfig portal = new ChannelProperties.SourceSystemConfig();
        portal.setChannels(Map.of("email", portalEmail, "sms", portalSms));
        ChannelProperties.SourceSystemConfig mobile = new ChannelProperties.SourceSystemConfig();
        mobile.setChannels(Map.of("email", mobileEmail));
        ChannelProperties properties = new ChannelProperties();
        properties.setSourceSystems(Map.of("portal", portal, "mobile", mobile));

        ResilientNotificationSender sender = new ResilientNotificationSender(null, properties, new TestPublisher(), 1, 0);
        NotificationRecipient recipient = recipient();

        sender.send(notification("portal"), recipient, ChannelType.EMAIL);
        assertThrows(RuntimeException.class, () -> sender.send(notification("portal"), recipient, ChannelType.EMAIL));
        sender.send(notification("portal"), recipient, ChannelType.SMS);
        sender.send(notification("mobile"), recipient, ChannelType.EMAIL);
    }

    private ChannelProperties.ChannelConfig enabledChannel(int rateLimitPerMinute) {
        ChannelProperties.ChannelConfig config = new ChannelProperties.ChannelConfig();
        config.setEnabled(true);
        config.setRateLimitPerMinute(rateLimitPerMinute);
        config.setRateLimitWindowSeconds(60);
        return config;
    }

    private Notification notification(String sourceSystem) {
        Notification notification = new Notification();
        notification.setNotificationId("n-" + sourceSystem);
        notification.setSourceSystem(sourceSystem);
        return notification;
    }

    private NotificationRecipient recipient() {
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-policy");
        return recipient;
    }

    private static final class TestPublisher implements NotificationDeliveryPublisher {
        private boolean sent;

        @Override
        public void publish(Notification notification, NotificationRecipient recipient, ChannelType channel) {
            sent = true;
        }
    }

    private static final class AlwaysFailingPublisher implements NotificationDeliveryPublisher {
        private int attempts;

        @Override
        public void publish(Notification notification, NotificationRecipient recipient, ChannelType channel) {
            attempts++;
            throw new IllegalStateException("persistent failure");
        }
    }

    private static final class FlakyPublisher implements NotificationDeliveryPublisher {
        private int failuresRemaining;
        private int attempts;

        private FlakyPublisher(int failuresRemaining) {
            this.failuresRemaining = failuresRemaining;
        }

        @Override
        public void publish(Notification notification, NotificationRecipient recipient, ChannelType channel) {
            attempts++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("transient failure");
            }
        }
    }
}

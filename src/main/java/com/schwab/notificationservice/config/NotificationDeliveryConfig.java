package com.schwab.notificationservice.config;

import com.schwab.notificationservice.delivery.MessageNotificationPublisher;
import com.schwab.notificationservice.delivery.NotificationDeliveryPublisher;
import com.schwab.notificationservice.delivery.NotificationRateLimiter;
import com.schwab.notificationservice.delivery.ResilientNotificationSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
public class NotificationDeliveryConfig {

    @Bean
    public NotificationDeliveryPublisher notificationDeliveryPublisher(NotificationDeliveryProperties properties) {
        return new MessageNotificationPublisher(properties.getMode());
    }

    @Bean
    public NotificationRateLimiter notificationRateLimiter(NotificationDeliveryProperties properties) {
        int perMinute = properties.getRateLimitPerMinute();
        int windowSeconds = properties.getRateLimitWindowSeconds();
        return new NotificationRateLimiter(perMinute, Duration.ofSeconds(windowSeconds));
    }

    @Bean
    public ResilientNotificationSender resilientNotificationSender(
            NotificationRateLimiter notificationRateLimiter,
            NotificationDeliveryPublisher notificationDeliveryPublisher,
            NotificationDeliveryProperties properties) {
        return new ResilientNotificationSender(
                notificationRateLimiter,
                notificationDeliveryPublisher,
                properties.getMaxAttempts(),
                properties.getRetryDelayMs());
    }
}

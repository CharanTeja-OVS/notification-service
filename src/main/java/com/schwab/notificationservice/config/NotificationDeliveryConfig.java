package com.schwab.notificationservice.config;

import com.schwab.notificationservice.delivery.MessageNotificationPublisher;
import com.schwab.notificationservice.delivery.NotificationDeliveryPublisher;
import com.schwab.notificationservice.delivery.ResilientNotificationSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
public class NotificationDeliveryConfig {

    @Bean
    public NotificationDeliveryPublisher notificationDeliveryPublisher(NotificationDeliveryProperties properties) {
        return new MessageNotificationPublisher(properties.getMode());
    }

    @Bean
    public ResilientNotificationSender resilientNotificationSender(
            ChannelProperties channelProperties,
            NotificationDeliveryPublisher notificationDeliveryPublisher,
            NotificationDeliveryProperties properties) {
        return new ResilientNotificationSender(
                null,
                channelProperties,
                notificationDeliveryPublisher,
                properties.getMaxAttempts(),
                properties.getRetryDelayMs());
    }
}

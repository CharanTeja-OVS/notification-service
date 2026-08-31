package com.schwab.notificationservice.delivery;

import com.schwab.notificationservice.domain.ChannelType;
import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageNotificationPublisher implements NotificationDeliveryPublisher {

    private static final Logger log = LoggerFactory.getLogger(MessageNotificationPublisher.class);

    private final String brokerType;

    public MessageNotificationPublisher(String brokerType) {
        this.brokerType = brokerType == null || brokerType.isBlank() ? "kafka" : brokerType;
    }

    @Override
    public void publish(Notification notification, NotificationRecipient recipient, ChannelType channel) {
        if (notification == null || recipient == null || channel == null) {
            throw new IllegalArgumentException("notification, recipient and channel are required");
        }
        log.info("Publishing notification {} to {} for recipient {} via {}", notification.getNotificationId(), brokerType, recipient.getRecipientId(), channel);
    }

    public String getBrokerType() {
        return brokerType;
    }
}

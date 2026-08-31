package com.schwab.notificationservice.delivery;

import com.schwab.notificationservice.domain.ChannelType;
import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationRecipient;

public interface NotificationDeliveryPublisher {
    void publish(Notification notification, NotificationRecipient recipient, ChannelType channel);
}

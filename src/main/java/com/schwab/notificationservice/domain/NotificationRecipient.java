package com.schwab.notificationservice.domain;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "recipient_id", nullable = false)
    private String recipientId;

    @Column(name = "contact_value", nullable = false)
    private String contactValue;

    @ElementCollection(targetClass = ChannelType.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "recipient_channel_preferences", joinColumns = @JoinColumn(name = "recipient_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private Set<ChannelType> preferredChannels = new HashSet<>();

    public NotificationRecipient() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getContactValue() {
        return contactValue;
    }

    public void setContactValue(String contactValue) {
        this.contactValue = contactValue;
    }

    public Set<ChannelType> getPreferredChannels() {
        return preferredChannels;
    }

    public void setPreferredChannels(Set<ChannelType> preferredChannels) {
        this.preferredChannels = preferredChannels;
    }
}

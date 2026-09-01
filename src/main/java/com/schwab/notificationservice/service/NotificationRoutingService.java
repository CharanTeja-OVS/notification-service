package com.schwab.notificationservice.service;

import com.schwab.notificationservice.config.ChannelProperties;
import com.schwab.notificationservice.domain.ChannelType;
import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationRecipient;
import com.schwab.notificationservice.domain.NotificationSeverity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NotificationRoutingService {

    private final ChannelProperties channelProperties;

    public NotificationRoutingService(ChannelProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    public List<ChannelType> determineChannels(Notification notification) {
        List<ChannelType> selected = new ArrayList<>();

        if (notification == null) {
            return selected;
        }
        if (hasSourceSystemPolicies() && sourceSystemConfig(notification.getSourceSystem()) == null) {
            return selected;
        }

        Set<ChannelType> candidateChannels = new LinkedHashSet<>();
        if (notification.getRecipients() != null) {
            for (NotificationRecipient recipient : notification.getRecipients()) {
                if (recipient == null || recipient.getPreferredChannels() == null) {
                    continue;
                }
                candidateChannels.addAll(recipient.getPreferredChannels().stream()
                        .filter(Objects::nonNull)
                        .toList());
            }
        }

        if (candidateChannels.isEmpty()) {
            candidateChannels.addAll(resolveConfiguredChannels(notification.getSourceSystem(), notification.getSeverity()));
            if (candidateChannels.isEmpty()) {
                switch (notification.getSeverity()) {
                    case CRITICAL -> candidateChannels.addAll(List.of(ChannelType.EMAIL, ChannelType.SMS, ChannelType.PUSH));
                    case HIGH -> candidateChannels.addAll(List.of(ChannelType.EMAIL, ChannelType.SMS));
                    case MEDIUM -> candidateChannels.addAll(List.of(ChannelType.EMAIL));
                    default -> candidateChannels.addAll(List.of(ChannelType.EMAIL));
                }
            }
        }

        candidateChannels.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .forEach(channel -> {
                    if (isEnabled(notification.getSourceSystem(), channel)) {
                        selected.add(channel);
                    }
                });

        return selected;
    }

    private List<ChannelType> resolveConfiguredChannels(String sourceSystem, NotificationSeverity severity) {
        ChannelProperties.SourceSystemConfig sourceSystemConfig = sourceSystemConfig(sourceSystem);
        if (sourceSystemConfig == null || sourceSystemConfig.getRouting() == null) {
            return List.of();
        }

        ChannelProperties.RoutingConfig routing = sourceSystemConfig.getRouting();
        if (severity != null && routing.getSeverityChannelOverrides() != null) {
            String severityKey = severity.name();
            List<String> configured = routing.getSeverityChannelOverrides().get(severityKey);
            if (configured == null) {
                configured = routing.getSeverityChannelOverrides().get(severityKey.toLowerCase(Locale.ROOT));
            }
            if (configured != null && !configured.isEmpty()) {
                return configured.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(String::toUpperCase)
                        .map(ChannelType::valueOf)
                        .toList();
            }
        }

        if (routing.getDefaultChannels() != null && !routing.getDefaultChannels().isEmpty()) {
            return routing.getDefaultChannels().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(String::toUpperCase)
                    .map(ChannelType::valueOf)
                    .toList();
        }

        return List.of();
    }

    private boolean isEnabled(String sourceSystem, ChannelType channelType) {
        if (channelType == null) {
            return false;
        }
        ChannelProperties.SourceSystemConfig sourceSystemConfig = sourceSystemConfig(sourceSystem);
        if (sourceSystemConfig == null || sourceSystemConfig.getChannels() == null) {
            return true;
        }
        String key = channelType.name().toLowerCase(Locale.ROOT);
        ChannelProperties.ChannelConfig config = sourceSystemConfig.getChannels().get(key);
        return config != null && config.isEnabled();
    }

    private ChannelProperties.SourceSystemConfig sourceSystemConfig(String sourceSystem) {
        return channelProperties == null ? null : channelProperties.getSourceSystem(sourceSystem);
    }

    private boolean hasSourceSystemPolicies() {
        return channelProperties != null && !channelProperties.getSourceSystems().isEmpty();
    }
}

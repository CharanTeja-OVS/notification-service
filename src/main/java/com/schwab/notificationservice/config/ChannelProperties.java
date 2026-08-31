package com.schwab.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "notification")
public class ChannelProperties {

    private Map<String, ChannelConfig> channels = new LinkedHashMap<>();
    private RoutingConfig routing = new RoutingConfig();

    public Map<String, ChannelConfig> getChannels() {
        return channels;
    }

    public void setChannels(Map<String, ChannelConfig> channels) {
        this.channels = channels == null ? new LinkedHashMap<>() : channels;
    }

    public RoutingConfig getRouting() {
        return routing;
    }

    public void setRouting(RoutingConfig routing) {
        this.routing = routing == null ? new RoutingConfig() : routing;
    }

    public static class RoutingConfig {
        private Map<String, List<String>> severityChannelOverrides = new LinkedHashMap<>();
        private List<String> defaultChannels = new ArrayList<>();

        public Map<String, List<String>> getSeverityChannelOverrides() {
            return severityChannelOverrides;
        }

        public void setSeverityChannelOverrides(Map<String, List<String>> severityChannelOverrides) {
            this.severityChannelOverrides = severityChannelOverrides == null ? new LinkedHashMap<>() : severityChannelOverrides;
        }

        public List<String> getDefaultChannels() {
            return defaultChannels;
        }

        public void setDefaultChannels(List<String> defaultChannels) {
            this.defaultChannels = defaultChannels == null ? new ArrayList<>() : defaultChannels;
        }
    }

    public static class ChannelConfig {
        private boolean enabled;
        private int maxAttempts;
        private long timeoutMs;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}

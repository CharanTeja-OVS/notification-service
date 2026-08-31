package com.schwab.notificationservice.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationRateLimiter {
    private final int maxPerWindow;
    private final Duration window;
    private final AtomicInteger count = new AtomicInteger();
    private volatile Instant windowStart = Instant.now();

    public NotificationRateLimiter(int maxPerWindow, Duration window) {
        if (maxPerWindow < 0) {
            throw new IllegalArgumentException("maxPerWindow must be >= 0");
        }
        this.maxPerWindow = maxPerWindow;
        this.window = window;
    }

    public synchronized boolean tryAcquire() {
        Instant now = Instant.now();
        if (!now.isBefore(windowStart.plus(window))) {
            count.set(0);
            windowStart = now;
        }

        if (maxPerWindow == 0) {
            return false;
        }

        int current = count.incrementAndGet();
        if (current > maxPerWindow) {
            count.decrementAndGet();
            return false;
        }
        return true;
    }
}

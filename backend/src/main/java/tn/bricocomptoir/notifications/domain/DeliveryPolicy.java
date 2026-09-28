package tn.bricocomptoir.notifications.domain;

import java.time.Duration;

public final class DeliveryPolicy {
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LEASE = Duration.ofMinutes(2);
    private DeliveryPolicy() { }
    public static Duration retryDelay(int attempt) {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) throw new IllegalArgumentException("Invalid attempt");
        return Duration.ofSeconds(Math.min(900, 15L << (attempt - 1)));
    }
}

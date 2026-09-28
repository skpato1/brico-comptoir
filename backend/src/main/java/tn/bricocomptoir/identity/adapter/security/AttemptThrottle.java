package tn.bricocomptoir.identity.adapter.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AttemptThrottle {
    private final Map<String, ArrayDeque<Instant>> attempts = new HashMap<>();
    private final Clock clock;

    public AttemptThrottle(Clock clock) { this.clock = clock; }

    public synchronized boolean allow(String scope, String source, int limit, Duration window) {
        Instant threshold = clock.instant().minus(window);
        Instant oldestRelevant = clock.instant().minus(Duration.ofHours(1));
        attempts.values().forEach(queue -> {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(oldestRelevant)) queue.removeFirst();
        });
        attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (attempts.size() > 10_000) return false;
        var queue = attempts.computeIfAbsent(scope + ':' + source, ignored -> new ArrayDeque<>());
        while (!queue.isEmpty() && queue.peekFirst().isBefore(threshold)) queue.removeFirst();
        if (queue.size() >= limit) return false;
        queue.addLast(clock.instant());
        return true;
    }
}

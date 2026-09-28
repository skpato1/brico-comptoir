package tn.bricocomptoir.identity.adapter.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AttemptThrottleTest {
    @Test
    void limitsRepeatedRequestsPerSourceWithoutBlockingAnotherSource() {
        var throttle = new AttemptThrottle(Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(throttle.allow("login", "127.0.0.1", 3, Duration.ofMinutes(15))).isTrue();
        }
        assertThat(throttle.allow("login", "127.0.0.1", 3, Duration.ofMinutes(15))).isFalse();
        assertThat(throttle.allow("login", "127.0.0.2", 3, Duration.ofMinutes(15))).isTrue();
    }
}

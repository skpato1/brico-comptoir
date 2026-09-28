package tn.bricocomptoir.notifications.domain;

import java.time.Instant;
import java.util.UUID;

public record Mail(UUID id, UUID leaseToken, int attempt, String recipient, String subject, String text,
                   Instant expiresAt) {
    public static void validate(String key, String recipient, String subject, String text, String resetScope) {
        if (key == null || key.isBlank() || key.length() > 180
                || recipient == null || recipient.length() > 254
                || recipient.chars().anyMatch(Character::isISOControl)
                || !recipient.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")
                || subject == null || subject.isBlank() || subject.length() > 200
                || subject.chars().anyMatch(Character::isISOControl)
                || text == null || text.isBlank() || text.length() > 100000
                || resetScope != null && !resetScope.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid email");
    }
}

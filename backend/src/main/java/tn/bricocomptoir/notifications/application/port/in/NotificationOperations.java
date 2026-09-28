package tn.bricocomptoir.notifications.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Calls must join the producer's transaction, never perform network I/O. */
public interface NotificationOperations {
    record Email(String key, String recipient, String subject, String text, String resetScope, Instant expiresAt) { }
    void enqueue(Email email);
    void cancelReset(String resetScope);
    void orderCreated(UUID orderId);
    void associate(String key,String ownerScope);
}

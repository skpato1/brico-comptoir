package tn.bricocomptoir.notifications.application.port.in;
import java.time.Instant;
import java.util.UUID;
public interface PersonalNotifications {
    void cancelOrders(String ownerScope);
    void cancelEmailChange(UUID accountId);
    int retain(Instant now,int days);
}

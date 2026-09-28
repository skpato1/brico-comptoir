package tn.bricocomptoir.notifications.adapter.transaction;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tn.bricocomptoir.notifications.application.port.in.NotificationOperations;
import tn.bricocomptoir.notifications.adapter.out.persistence.JdbcNotifications;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class NotificationTransactions implements NotificationOperations {
    private final JdbcNotifications store;
    public NotificationTransactions(JdbcNotifications store) { this.store=store; }
    @Override public void enqueue(Email email) { store.enqueue(email); }
    @Override public void cancelReset(String scope) { store.cancelReset(scope); }
    @Override public void orderCreated(UUID orderId) { store.created(orderId); }
    @Override public void associate(String key,String scope) { store.associate(key,scope); }
}

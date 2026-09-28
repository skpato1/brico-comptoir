package tn.bricocomptoir.sales.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.sales.domain.OrderModels.*;

public interface OrderStore {
    default void lockGuestPurchase(String scope) { }
    Receipt acquire(String owner, UUID key, String fingerprint);
    void complete(String owner, UUID key, UUID orderId);
    void insert(Order order);
    Optional<Order> find(UUID id, boolean lock);
    List<Order> list(String owner, Status status, int offset, int size);
    void transition(UUID id, Status status, String actorScope);
}

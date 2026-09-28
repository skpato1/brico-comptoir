package tn.bricocomptoir.sales.adapter.transaction;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tn.bricocomptoir.sales.application.service.OrderService;
import tn.bricocomptoir.sales.domain.CheckoutFailure;
import tn.bricocomptoir.sales.domain.OrderModels.*;

@Service
public class OrderTransactions {
    private final OrderService service;
    private final TransactionTemplate placement;
    public OrderTransactions(OrderService service, PlatformTransactionManager manager) {
        this.service = service;
        this.placement = new TransactionTemplate(manager);
        placement.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        placement.setTimeout(20);
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot preview(Actor actor, List<Item> items, Address address) { return service.preview(actor, items, address); }
    public Order place(Actor actor, UUID key, List<Item> items, Address address, String hash) {
        return retry(() -> placement.execute(status -> service.place(actor, key, items, address, hash)));
    }
    private Order retry(Supplier<Order> work) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try { return work.get(); }
            catch (CannotAcquireLockException | CannotSerializeTransactionException collision) {
                if (attempt == 2) throw new CheckoutFailure("STOCK_UNAVAILABLE");
            }
        }
        throw new IllegalStateException("Unreachable");
    }
    @Transactional(readOnly = true)
    public Order get(Actor actor, UUID id) { return service.get(actor, id, false); }
    @Transactional(readOnly = true)
    public List<Order> list(Actor actor, Status status, int page, int size) { return service.list(actor, status, page, size); }
    @Transactional
    public Order transition(Actor actor, UUID id, Status target) { return service.transition(actor, id, target); }
}

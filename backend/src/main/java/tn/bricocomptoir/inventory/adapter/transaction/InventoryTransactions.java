package tn.bricocomptoir.inventory.adapter.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.inventory.application.port.in.InventoryOperations;
import tn.bricocomptoir.inventory.application.port.in.InventoryAvailabilityQueries;
import tn.bricocomptoir.inventory.application.service.InventoryService;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;

@Primary
@Service
public class InventoryTransactions implements InventoryOperations, InventoryAvailabilityQueries,
        tn.bricocomptoir.inventory.application.port.in.InventoryOrderOperations {
    private final InventoryService service;
    public InventoryTransactions(InventoryService service) { this.service = service; }

    @Override @Transactional(readOnly = true)
    public Stock stock(UUID variantId) { return service.stock(variantId); }
    @Override @Transactional(readOnly = true)
    public long available(UUID variantId) { return service.available(variantId); }
    @Override @Transactional(readOnly = true)
    public long publicAvailability(UUID variantId) { return service.publicAvailability(variantId); }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Stock adjust(UUID operationId, UUID variantId, long delta, String reason, String actorName) {
        return service.adjust(operationId, variantId, delta, reason, actorName);
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Reservation reserve(UUID reservationId, List<Line> lines) {
        return service.reserve(reservationId, lines);
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Reservation release(UUID reservationId) { return service.release(reservationId); }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Reservation consume(UUID reservationId) { return service.consume(reservationId); }

    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reserveForOrder(UUID id, java.util.Map<UUID, Long> demand) {
        service.reserve(id, demand.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue())).toList());
    }
    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void releaseForOrder(UUID id) { service.release(id); }
    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void consumeForOrder(UUID id) { service.consume(id); }
}

package tn.bricocomptoir.inventory.application.port.in;

import java.util.List;
import java.util.UUID;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;

/** Synchronous stock operations. Callers must share the surrounding transaction. */
public interface InventoryOperations {
    Stock stock(UUID variantId);
    long publicAvailability(UUID variantId);
    Stock adjust(UUID operationId, UUID variantId, long delta, String reason, String actorName);
    Reservation reserve(UUID reservationId, List<Line> lines);
    Reservation release(UUID reservationId);
    Reservation consume(UUID reservationId);
}

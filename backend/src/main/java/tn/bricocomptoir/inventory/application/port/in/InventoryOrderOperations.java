package tn.bricocomptoir.inventory.application.port.in;

import java.util.Map;
import java.util.UUID;

/** Commands for sales in the caller's transaction; only JDK values cross the module boundary. */
public interface InventoryOrderOperations {
    void reserveForOrder(UUID orderId, Map<UUID, Long> demand);
    void releaseForOrder(UUID orderId);
    void consumeForOrder(UUID orderId);
}

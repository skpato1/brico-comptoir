package tn.bricocomptoir.inventory.application.port.in;

import java.util.UUID;

/** Read-only SKU availability for other modules; never a purchase guarantee. */
public interface InventoryAvailabilityQueries {
    long available(UUID variantId);
}

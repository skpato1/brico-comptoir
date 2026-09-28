package tn.bricocomptoir.packs.adapter.out.module;

import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.inventory.application.port.in.InventoryAvailabilityQueries;
import tn.bricocomptoir.packs.application.port.out.StockAvailabilityLookup;

@Component
public class InventoryAvailabilityAdapter implements StockAvailabilityLookup {
    private final InventoryAvailabilityQueries inventory;
    public InventoryAvailabilityAdapter(InventoryAvailabilityQueries inventory) { this.inventory = inventory; }
    @Override public long available(UUID id) { return inventory.available(id); }
}

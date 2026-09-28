package tn.bricocomptoir.sales.adapter.out.module;

import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.inventory.application.port.in.InventoryAvailabilityQueries;
import tn.bricocomptoir.sales.application.port.out.StockLookup;

@Component
public class InventoryStockAdapter implements StockLookup {
    private final InventoryAvailabilityQueries inventory;
    public InventoryStockAdapter(InventoryAvailabilityQueries inventory) { this.inventory = inventory; }
    @Override public long available(UUID skuId) { return inventory.available(skuId); }
}

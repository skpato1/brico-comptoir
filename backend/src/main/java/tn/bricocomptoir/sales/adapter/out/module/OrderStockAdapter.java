package tn.bricocomptoir.sales.adapter.out.module;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.inventory.application.port.in.InventoryOrderOperations;
import tn.bricocomptoir.sales.application.port.out.OrderStock;
import tn.bricocomptoir.sales.domain.CheckoutFailure;

@Component
public class OrderStockAdapter implements OrderStock {
    private final InventoryOrderOperations stock;
    public OrderStockAdapter(InventoryOrderOperations stock) { this.stock = stock; }
    @Override public void reserve(UUID id, Map<UUID, Long> demand) {
        try { stock.reserveForOrder(id, demand); }
        catch (IllegalStateException | IllegalArgumentException rejected) { throw new CheckoutFailure("STOCK_UNAVAILABLE"); }
    }
    @Override public void release(UUID id) { stock.releaseForOrder(id); }
    @Override public void consume(UUID id) { stock.consumeForOrder(id); }
}

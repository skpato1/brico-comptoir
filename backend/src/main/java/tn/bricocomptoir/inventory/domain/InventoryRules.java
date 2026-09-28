package tn.bricocomptoir.inventory.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;

public final class InventoryRules {
    private InventoryRules() { }

    public static List<Line> normalize(List<Line> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > 100)
            throw new IllegalArgumentException("Reservation requires 1 to 100 lines");
        Map<UUID, Long> quantities = new TreeMap<>(Comparator.naturalOrder());
        for (Line line : requested) {
            if (line == null || line.variantId() == null || line.quantity() <= 0)
                throw new IllegalArgumentException("Each SKU needs a positive quantity");
            try {
                quantities.merge(line.variantId(), line.quantity(), Math::addExact);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Quantity overflow", overflow);
            }
        }
        List<Line> result = new ArrayList<>();
        quantities.forEach((id, quantity) -> result.add(new Line(id, quantity)));
        return List.copyOf(result);
    }

    public static Stock change(Stock stock, long onHandDelta, long reservedDelta) {
        try {
            long onHand = Math.addExact(stock.onHand(), onHandDelta);
            long reserved = Math.addExact(stock.reserved(), reservedDelta);
            if (onHand < 0 || reserved < 0 || reserved > onHand)
                throw new IllegalStateException("Insufficient available stock");
            return new Stock(stock.variantId(), onHand, reserved);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Stock quantity overflow", overflow);
        }
    }

    public static Status transition(Status current, Status target) {
        if (current == target) return current;
        if (current != Status.ACTIVE || target == Status.ACTIVE)
            throw new IllegalStateException("Invalid reservation transition");
        return target;
    }
}

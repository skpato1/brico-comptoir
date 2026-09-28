package tn.bricocomptoir.inventory.domain;

import java.util.List;
import java.util.UUID;

public final class InventoryModels {
    private InventoryModels() { }

    public record Stock(UUID variantId, long onHand, long reserved) {
        public long available() { return onHand - reserved; }
    }

    public record Line(UUID variantId, long quantity) { }

    public enum Status { ACTIVE, RELEASED, CONSUMED }

    public record Reservation(UUID id, Status status, List<Line> lines) { }

    public record Adjustment(UUID id, UUID variantId, long delta, String reason) { }

    public enum MovementType { ADJUST, RESERVE, RELEASE, CONSUME }

    public record Movement(UUID id, UUID variantId, UUID reservationId, UUID operationId,
                           String actorName, MovementType type, long onHandDelta,
                           long reservedDelta, String reason) { }
}

package tn.bricocomptoir.inventory.application.service;

import java.util.List;
import java.util.UUID;
import tn.bricocomptoir.inventory.application.port.in.InventoryOperations;
import tn.bricocomptoir.inventory.application.port.in.InventoryAvailabilityQueries;
import tn.bricocomptoir.inventory.application.port.out.InventoryStore;
import tn.bricocomptoir.inventory.application.port.out.VariantReferencePort;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;
import tn.bricocomptoir.inventory.domain.InventoryRules;

public final class InventoryService implements InventoryOperations, InventoryAvailabilityQueries {
    private final InventoryStore store;
    private final VariantReferencePort variants;

    public InventoryService(InventoryStore store, VariantReferencePort variants) {
        this.store = store; this.variants = variants;
    }

    @Override public Stock stock(UUID variantId) {
        requireVariant(variantId);
        return store.stock(variantId).orElse(new Stock(variantId, 0, 0));
    }

    @Override public long available(UUID variantId) { return stock(variantId).available(); }

    @Override public long publicAvailability(UUID variantId) {
        if (variantId == null || !variants.published(variantId))
            throw new IllegalArgumentException("Variant not found");
        return store.stock(variantId).map(Stock::available).orElse(0L);
    }

    @Override public Stock adjust(UUID operationId, UUID variantId, long delta, String reason, String actorName) {
        if (operationId == null || actorName == null || actorName.isBlank() || delta == 0)
            throw new IllegalArgumentException("Operation, actor and nonzero delta required");
        String explanation = reason == null ? "" : reason.trim();
        if (explanation.isEmpty() || explanation.length() > 500)
            throw new IllegalArgumentException("Adjustment reason required (max 500 characters)");
        requireVariant(variantId);
        Adjustment requested = new Adjustment(operationId, variantId, delta, explanation);
        boolean created = store.createAdjustmentIfAbsent(requested);
        Adjustment prior = store.lockAdjustment(operationId);
        if (!requested.equals(prior)) throw new IllegalStateException("Adjustment operation conflicts with existing data");
        if (!created) return stock(variantId);
        store.ensureStock(variantId);
        Stock changed = InventoryRules.change(store.lockStock(variantId), delta, 0);
        store.updateStock(changed);
        store.addMovement(new Movement(UUID.randomUUID(), variantId, null, operationId, actorName,
                MovementType.ADJUST, delta, 0, explanation));
        return changed;
    }

    @Override public Reservation reserve(UUID reservationId, List<Line> lines) {
        if (reservationId == null) throw new IllegalArgumentException("Reservation ID required");
        List<Line> requested = InventoryRules.normalize(lines);
        boolean created = store.createReservationIfAbsent(reservationId);
        Reservation prior = store.lockReservation(reservationId);
        if (!created) {
            if (!prior.lines().equals(requested))
                throw new IllegalStateException("Reservation ID conflicts with existing lines");
            if (prior.status() != Status.ACTIVE)
                throw new IllegalStateException("Reservation is no longer active");
            return prior;
        }
        for (Line line : requested) {
            if (!variants.published(line.variantId())) throw new IllegalArgumentException("Variant not found");
        }
        // The store locks one row per call. Iterating normalized lines fixes the lock order.
        List<Stock> changed = new java.util.ArrayList<>();
        for (Line line : requested) {
            Stock locked = store.lockStock(line.variantId());
            changed.add(InventoryRules.change(locked, 0, line.quantity()));
        }
        for (int i = 0; i < requested.size(); i++) {
            Line line = requested.get(i);
            store.updateStock(changed.get(i));
            store.addMovement(new Movement(UUID.randomUUID(), line.variantId(), reservationId,
                    null, null, MovementType.RESERVE, 0, line.quantity(), ""));
        }
        store.addReservationLines(reservationId, requested);
        return new Reservation(reservationId, Status.ACTIVE, requested);
    }

    @Override public Reservation release(UUID reservationId) { return finish(reservationId, Status.RELEASED); }
    @Override public Reservation consume(UUID reservationId) { return finish(reservationId, Status.CONSUMED); }

    private Reservation finish(UUID reservationId, Status target) {
        if (reservationId == null) throw new IllegalArgumentException("Reservation ID required");
        Reservation prior = store.lockReservation(reservationId);
        InventoryRules.transition(prior.status(), target);
        if (prior.status() == target) return prior;
        List<Stock> changed = new java.util.ArrayList<>();
        for (Line line : prior.lines()) {
            Stock locked = store.lockStock(line.variantId());
            long physicalDelta = target == Status.CONSUMED ? -line.quantity() : 0;
            changed.add(InventoryRules.change(locked, physicalDelta, -line.quantity()));
        }
        for (int i = 0; i < prior.lines().size(); i++) {
            Line line = prior.lines().get(i);
            store.updateStock(changed.get(i));
            store.addMovement(new Movement(UUID.randomUUID(), line.variantId(), reservationId,
                    null, null, target == Status.CONSUMED ? MovementType.CONSUME : MovementType.RELEASE,
                    target == Status.CONSUMED ? -line.quantity() : 0, -line.quantity(), ""));
        }
        store.updateReservationStatus(reservationId, target);
        return new Reservation(reservationId, target, prior.lines());
    }

    private void requireVariant(UUID id) {
        if (id == null || !variants.exists(id)) throw new IllegalArgumentException("Variant not found");
    }
}

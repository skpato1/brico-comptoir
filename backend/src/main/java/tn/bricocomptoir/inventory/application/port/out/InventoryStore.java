package tn.bricocomptoir.inventory.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;

public interface InventoryStore {
    Optional<Stock> stock(UUID variantId);
    void ensureStock(UUID variantId);
    Stock lockStock(UUID variantId);
    void updateStock(Stock stock);
    boolean createReservationIfAbsent(UUID id);
    Reservation lockReservation(UUID id);
    void addReservationLines(UUID id, List<Line> lines);
    void updateReservationStatus(UUID id, Status status);
    boolean createAdjustmentIfAbsent(Adjustment adjustment);
    Adjustment lockAdjustment(UUID id);
    void addMovement(Movement movement);
}

package tn.bricocomptoir.inventory.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.inventory.application.port.out.InventoryStore;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;

@Repository
public class JdbcInventoryStore implements InventoryStore {
    private final JdbcTemplate jdbc;
    public JdbcInventoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Stock> stock(UUID id) {
        return jdbc.query("SELECT variant_id, on_hand, reserved FROM inventory_stock WHERE variant_id = ?",
                this::stockRow, id).stream().findFirst();
    }

    @Override public void ensureStock(UUID id) {
        jdbc.update("INSERT INTO inventory_stock(variant_id) VALUES (?) ON CONFLICT DO NOTHING", id);
    }

    @Override public Stock lockStock(UUID id) {
        return jdbc.query("SELECT variant_id, on_hand, reserved FROM inventory_stock "
                        + "WHERE variant_id = ? FOR UPDATE", this::stockRow, id)
                .stream().findFirst().orElse(new Stock(id, 0, 0));
    }

    @Override public void updateStock(Stock stock) {
        int count = jdbc.update("UPDATE inventory_stock SET on_hand = ?, reserved = ?, updated_at = now() "
                        + "WHERE variant_id = ?", stock.onHand(), stock.reserved(), stock.variantId());
        if (count != 1) throw new IllegalStateException("Stock row missing");
    }

    @Override public boolean createReservationIfAbsent(UUID id) {
        return jdbc.update("INSERT INTO inventory_reservation(id) VALUES (?) ON CONFLICT DO NOTHING", id) == 1;
    }

    @Override public Reservation lockReservation(UUID id) {
        Status status;
        try {
            status = jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE id = ? FOR UPDATE",
                    (row, number) -> Status.valueOf(row.getString(1)), id);
        } catch (EmptyResultDataAccessException missing) {
            throw new IllegalArgumentException("Reservation not found", missing);
        }
        List<Line> lines = new ArrayList<>(jdbc.query("SELECT variant_id, quantity FROM inventory_reservation_line "
                        + "WHERE reservation_id = ?", (row, number) -> new Line(row.getObject(1, UUID.class),
                        row.getLong(2)), id));
        lines.sort(Comparator.comparing(Line::variantId));
        return new Reservation(id, status, List.copyOf(lines));
    }

    @Override public void addReservationLines(UUID id, List<Line> lines) {
        for (Line line : lines) jdbc.update("INSERT INTO inventory_reservation_line"
                + "(reservation_id, variant_id, quantity) VALUES (?, ?, ?)", id, line.variantId(), line.quantity());
    }

    @Override public void updateReservationStatus(UUID id, Status status) {
        jdbc.update("UPDATE inventory_reservation SET status = ?, updated_at = now() WHERE id = ?",
                status.name(), id);
    }

    @Override public boolean createAdjustmentIfAbsent(Adjustment adjustment) {
        return jdbc.update("INSERT INTO inventory_adjustment(id, variant_id, delta, reason) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", adjustment.id(),
                adjustment.variantId(), adjustment.delta(), adjustment.reason()) == 1;
    }

    @Override public Adjustment lockAdjustment(UUID id) {
        return jdbc.queryForObject("SELECT id, variant_id, delta, reason FROM inventory_adjustment "
                        + "WHERE id = ? FOR UPDATE", (row, number) -> new Adjustment(
                row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getLong(3), row.getString(4)), id);
    }

    @Override public void addMovement(Movement movement) {
        jdbc.update("INSERT INTO inventory_movement(id, variant_id, reservation_id, operation_id, actor_name, "
                        + "type, on_hand_delta, reserved_delta, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                movement.id(), movement.variantId(), movement.reservationId(), movement.operationId(),
                movement.actorName(), movement.type().name(), movement.onHandDelta(),
                movement.reservedDelta(), movement.reason());
    }

    private Stock stockRow(ResultSet row, int number) throws SQLException {
        return new Stock(row.getObject(1, UUID.class), row.getLong(2), row.getLong(3));
    }
}

package tn.bricocomptoir.sales.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.sales.application.port.out.CartStore;
import tn.bricocomptoir.sales.domain.CartModels.*;

@Repository
public class JdbcCartStore implements CartStore {
    private final JdbcTemplate jdbc;
    public JdbcCartStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Cart> read(UUID customerId) {
        return jdbc.query("SELECT version FROM customer_cart WHERE customer_id = ?",
                (rs, row) -> new Cart(customerId, rs.getLong(1), lines(customerId)), customerId)
                .stream().findFirst();
    }

    @Override public Cart lock(UUID customerId) {
        jdbc.update("INSERT INTO customer_cart(customer_id) VALUES (?) ON CONFLICT DO NOTHING", customerId);
        long version = jdbc.queryForObject(
                "SELECT version FROM customer_cart WHERE customer_id = ? FOR UPDATE", Long.class, customerId);
        return new Cart(customerId, version, lines(customerId));
    }

    @Override public Cart save(Cart cart) {
        int updated = jdbc.update("UPDATE customer_cart SET version = version + 1, updated_at = now() "
                        + "WHERE customer_id = ? AND version = ?", cart.customerId(), cart.version());
        if (updated != 1) throw new IllegalStateException("Cart version conflict");
        jdbc.update("DELETE FROM customer_cart_line WHERE customer_id = ?", cart.customerId());
        for (Line line : cart.lines()) {
            jdbc.update("INSERT INTO customer_cart_line(customer_id, kind, offer_id, quantity) VALUES (?, ?, ?, ?)",
                    cart.customerId(), line.kind().name(), line.offerId(), line.quantity());
        }
        return new Cart(cart.customerId(), cart.version() + 1, List.copyOf(cart.lines()));
    }

    @Override public Optional<String> mergeFingerprint(UUID customerId, UUID mergeId) {
        return jdbc.query("SELECT fingerprint FROM customer_cart_merge WHERE customer_id = ? AND merge_id = ?",
                (rs, row) -> rs.getString(1).trim(), customerId, mergeId).stream().findFirst();
    }

    @Override public void recordMerge(UUID customerId, UUID mergeId, String fingerprint) {
        jdbc.update("INSERT INTO customer_cart_merge(customer_id, merge_id, fingerprint) VALUES (?, ?, ?)",
                customerId, mergeId, fingerprint);
    }

    private List<Line> lines(UUID customerId) {
        return jdbc.query("SELECT kind, offer_id, quantity FROM customer_cart_line WHERE customer_id = ? "
                        + "ORDER BY CASE kind WHEN 'PRODUCT' THEN 0 ELSE 1 END, offer_id",
                (rs, row) -> new Line(Kind.valueOf(rs.getString(1)),
                        rs.getObject(2, UUID.class), rs.getLong(3)), customerId);
    }
}

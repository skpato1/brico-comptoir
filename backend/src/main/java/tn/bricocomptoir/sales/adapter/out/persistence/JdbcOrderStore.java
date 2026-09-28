package tn.bricocomptoir.sales.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.sales.application.port.out.OrderStore;
import tn.bricocomptoir.sales.domain.OrderModels.*;

@Repository
public class JdbcOrderStore implements OrderStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcOrderStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public void lockGuestPurchase(String scope) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,92718))",Object.class,scope);
        if(jdbc.queryForObject("SELECT count(*) FROM sales_privacy_closed_scope WHERE owner_scope=?",Long.class,scope)>0)
            throw new tn.bricocomptoir.sales.domain.CheckoutFailure("GUEST_SESSION_CLOSED");
    }
    @Override public Receipt acquire(String owner, UUID key, String fingerprint) {
        jdbc.update("INSERT INTO sales_order_request(owner_scope, request_key, fingerprint) VALUES (?,?,?) "
                + "ON CONFLICT DO NOTHING", owner, key, fingerprint);
        return jdbc.queryForObject("SELECT fingerprint, order_id FROM sales_order_request "
                + "WHERE owner_scope=? AND request_key=? FOR UPDATE",
                (rs, n) -> new Receipt(rs.getString(1).trim(), rs.getObject(2, UUID.class)), owner, key);
    }
    @Override public void complete(String owner, UUID key, UUID id) {
        jdbc.update("UPDATE sales_order_request SET order_id=? WHERE owner_scope=? AND request_key=? AND order_id IS NULL",
                id, owner, key);
    }
    @Override public void insert(Order order) {
        Snapshot s = order.snapshot();
        jdbc.update("INSERT INTO sales_order(id,owner_scope,customer_id,status,snapshot,subtotal_tnd,delivery_tnd,total_tnd,created_at) "
                + "VALUES (?,?,?,?,?::jsonb,?,?,?,?)", order.id(), order.ownerScope(), order.customerId(), order.status().name(),
                json.writeValueAsString(new Snapshot(removedAddress(),s.items(),s.subtotalTnd(),s.deliveryTnd(),s.totalTnd(),s.quoteHash())),
                s.subtotalTnd(), s.deliveryTnd(), s.totalTnd(), java.sql.Timestamp.from(order.createdAt()));
        jdbc.update("INSERT INTO sales_order_contact(order_id,address) VALUES (?,?::jsonb)",order.id(),json.writeValueAsString(s.address()));
        event(order.id(), order.status(), order.ownerScope());
    }
    @Override public Optional<Order> find(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM sales_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::row, id)
                .stream().findFirst();
    }
    @Override public List<Order> list(String owner, Status status, int offset, int size) {
        String sql = "SELECT * FROM sales_order WHERE true";
        List<Object> parameters = new ArrayList<>();
        if (owner != null) { sql += " AND owner_scope=?"; parameters.add(owner); }
        if (status != null) { sql += " AND status=?"; parameters.add(status.name()); }
        parameters.add(size); parameters.add(offset);
        return jdbc.query(sql + " ORDER BY created_at DESC,id LIMIT ? OFFSET ?", this::row, parameters.toArray());
    }
    @Override public void transition(UUID id, Status status, String actor) {
        jdbc.update("UPDATE sales_order SET status=?, updated_at=now() WHERE id=?", status.name(), id);
        event(id, status, actor);
    }
    private void event(UUID id, Status status, String actor) {
        jdbc.update("INSERT INTO sales_order_event(id,order_id,status,actor_scope) VALUES (?,?,?,?)",
                UUID.randomUUID(), id, status.name(), actor);
    }
    private Order row(ResultSet rs, int number) throws SQLException {
        UUID id=rs.getObject("id",UUID.class);
        Snapshot s=json.readValue(rs.getString("snapshot"),Snapshot.class);
        var address=jdbc.query("SELECT address FROM sales_order_contact WHERE order_id=?",(r,n)->json.readValue(r.getString(1),Address.class),id);
        return new Order(rs.getObject("id", UUID.class), rs.getString("owner_scope"),
                rs.getObject("customer_id", UUID.class), Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),new Snapshot(address.isEmpty()?removedAddress():address.getFirst(),
                    s.items(),s.subtotalTnd(),s.deliveryTnd(),s.totalTnd(),s.quoteHash()));
    }
    public static Address removedAddress() { return new Address("Coordonnées retirées","","","","","","TN",null); }
}

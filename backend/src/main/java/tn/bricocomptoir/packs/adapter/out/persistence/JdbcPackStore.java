package tn.bricocomptoir.packs.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.packs.application.port.out.PackStore;
import tn.bricocomptoir.packs.domain.PackModels.*;

@Repository
public class JdbcPackStore implements PackStore {
    private final JdbcTemplate jdbc;
    public JdbcPackStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<Pack> packs() {
        return jdbc.query("SELECT id FROM pack_offer ORDER BY lower(name), id",
                (row, number) -> row.getObject(1, UUID.class)).stream()
                .map(id -> pack(id).orElseThrow()).toList();
    }
    @Override public Page<Pack> page(String q,int page,int size) {
        String where=" WHERE strpos(lower(name),lower(?))>0 OR strpos(lower(code),lower(?))>0";
        long count=jdbc.queryForObject("SELECT count(*) FROM pack_offer" + where,Long.class,q,q);
        var items=jdbc.query("SELECT id FROM pack_offer" + where + " ORDER BY lower(name),id LIMIT ? OFFSET ?",
                (r,n)->r.getObject(1,UUID.class),q,q,size,(long)page*size).stream().map(id->pack(id).orElseThrow()).toList();
        return new Page<>(items,page,size,count);
    }

    @Override public Optional<Pack> pack(UUID id) {
        return jdbc.query("SELECT * FROM pack_offer WHERE id = ?", (row, number) -> new Pack(
                row.getObject("id", UUID.class), row.getString("code"), row.getString("name"),
                row.getString("slogan"), row.getString("guide"), row.getString("status"),
                row.getBoolean("demo"), row.getLong("version"), variants(id)), id).stream().findFirst();
    }

    @Override public Optional<PackVariant> variant(UUID id) {
        return jdbc.query("SELECT * FROM pack_variant WHERE id = ?", this::variantRow, id).stream().findFirst();
    }

    @Override public Pack savePack(Pack pack, boolean create) {
        if (create) {
            jdbc.update("INSERT INTO pack_offer(id, code, name, slogan, guide, status, demo) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", pack.id(), pack.code(), pack.name(),
                    pack.slogan(), pack.guide(), pack.status(), pack.demo());
        } else if (jdbc.update("UPDATE pack_offer SET code = ?, name = ?, slogan = ?, guide = ?, "
                        + "status = ?, version = version + 1, updated_at = now() "
                        + "WHERE id = ? AND version = ?", pack.code(), pack.name(), pack.slogan(),
                pack.guide(), pack.status(), pack.id(), pack.version()) != 1) {
            throw new IllegalStateException("Version conflict");
        }
        return pack(pack.id()).orElseThrow();
    }

    @Override public PackVariant saveVariant(PackVariant variant, boolean create) {
        if (create) {
            jdbc.update("INSERT INTO pack_variant(id, pack_id, code, label, price_tnd, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", variant.id(), variant.packId(), variant.code(),
                    variant.label(), variant.priceTnd(), variant.status());
        } else if (jdbc.update("UPDATE pack_variant SET code = ?, label = ?, price_tnd = ?, status = ?, "
                        + "version = version + 1, updated_at = now() WHERE id = ? AND version = ?",
                variant.code(), variant.label(), variant.priceTnd(), variant.status(),
                variant.id(), variant.version()) != 1) {
            throw new IllegalStateException("Version conflict");
        }
        if (!create) jdbc.update("DELETE FROM pack_component WHERE pack_variant_id = ?", variant.id());
        for (Component component : variant.components()) {
            jdbc.update("INSERT INTO pack_component(pack_variant_id, catalog_variant_id, quantity) VALUES (?, ?, ?)",
                    variant.id(), component.variantId(), component.quantity());
        }
        return variant(variant.id()).orElseThrow();
    }

    private List<PackVariant> variants(UUID packId) {
        return jdbc.query("SELECT * FROM pack_variant WHERE pack_id = ? ORDER BY code, id",
                this::variantRow, packId);
    }

    private PackVariant variantRow(ResultSet row, int number) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        return new PackVariant(id, row.getObject("pack_id", UUID.class), row.getString("code"),
                row.getString("label"), row.getBigDecimal("price_tnd"), row.getString("status"),
                row.getLong("version"), jdbc.query("SELECT catalog_variant_id, quantity FROM pack_component "
                        + "WHERE pack_variant_id = ? ORDER BY catalog_variant_id",
                (component, index) -> new Component(component.getObject(1, UUID.class),
                        component.getLong(2)), id));
    }
}

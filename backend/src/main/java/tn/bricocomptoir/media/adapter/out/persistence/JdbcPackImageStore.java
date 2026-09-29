package tn.bricocomptoir.media.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.media.application.port.out.PackImageStore;
import tn.bricocomptoir.media.domain.PackImage;

@Repository
public class JdbcPackImageStore implements PackImageStore {
    private final JdbcTemplate jdbc;
    public JdbcPackImageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<PackImage> ROW = (rs, row) -> map(rs);
    private static PackImage map(ResultSet rs) throws SQLException {
        return new PackImage(rs.getObject("id", UUID.class), rs.getObject("pack_id", UUID.class),
                rs.getString("original_key"), rs.getString("card_key"), rs.getString("detail_key"),
                rs.getString("source_mime"), rs.getLong("source_bytes"), rs.getString("source_sha256"),
                rs.getInt("width"), rs.getInt("height"), rs.getInt("sort_order"), rs.getBoolean("is_primary"),
                rs.getTimestamp("created_at").toInstant());
    }
    @Override public void lockPack(UUID packId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",
                packId.getMostSignificantBits() ^ packId.getLeastSignificantBits());
    }
    @Override public List<PackImage> images(UUID packId) {
        return jdbc.query("SELECT * FROM media_pack_image WHERE pack_id=? ORDER BY sort_order", ROW, packId);
    }
    @Override public Optional<PackImage> image(UUID id) {
        return jdbc.query("SELECT * FROM media_pack_image WHERE id=?", ROW, id).stream().findFirst();
    }
    @Override public void insert(PackImage image) {
        jdbc.update("INSERT INTO media_pack_image(id,pack_id,original_key,card_key,detail_key,source_mime,"
                + "source_bytes,source_sha256,width,height,sort_order,is_primary) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                image.id(), image.packId(), image.originalKey(), image.cardKey(), image.detailKey(),
                image.sourceMime(), image.sourceBytes(), image.sourceSha256(), image.width(), image.height(),
                image.sortOrder(), image.primary());
    }
    @Override public void reorder(UUID packId, List<UUID> ids, UUID primary) {
        jdbc.update("UPDATE media_pack_image SET is_primary=false WHERE pack_id=?", packId);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE media_pack_image SET sort_order=?,is_primary=? WHERE id=? AND pack_id=?",
                    i, ids.get(i).equals(primary), ids.get(i), packId);
        }
    }
}

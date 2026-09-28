package tn.bricocomptoir.media.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.media.application.port.out.MediaStore;
import tn.bricocomptoir.media.domain.ProductImage;

@Repository
public class JdbcMediaStore implements MediaStore {
    private final JdbcTemplate jdbc;
    public JdbcMediaStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<ProductImage> ROW = (rs, row) -> map(rs);
    private static ProductImage map(ResultSet rs) throws SQLException {
        return new ProductImage(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("original_key"), rs.getString("card_key"), rs.getString("detail_key"),
                rs.getString("source_mime"), rs.getLong("source_bytes"), rs.getString("source_sha256"),
                rs.getInt("width"), rs.getInt("height"), rs.getInt("sort_order"), rs.getBoolean("is_primary"),
                rs.getTimestamp("created_at").toInstant());
    }
    @Override public void lockProduct(UUID productId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",
                productId.getMostSignificantBits() ^ productId.getLeastSignificantBits());
    }
    @Override public List<ProductImage> images(UUID productId) {
        return jdbc.query("SELECT * FROM media_product_image WHERE product_id=? ORDER BY sort_order", ROW, productId);
    }
    @Override public Optional<ProductImage> image(UUID id) {
        return jdbc.query("SELECT * FROM media_product_image WHERE id=?", ROW, id).stream().findFirst();
    }
    @Override public void insert(ProductImage image) {
        jdbc.update("INSERT INTO media_product_image(id,product_id,original_key,card_key,detail_key,source_mime,"
                + "source_bytes,source_sha256,width,height,sort_order,is_primary) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                image.id(), image.productId(), image.originalKey(), image.cardKey(), image.detailKey(),
                image.sourceMime(), image.sourceBytes(), image.sourceSha256(), image.width(), image.height(),
                image.sortOrder(), image.primary());
    }
    @Override public void reorder(UUID productId, List<UUID> ids, UUID primary) {
        jdbc.update("UPDATE media_product_image SET is_primary=false WHERE product_id=?", productId);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE media_product_image SET sort_order=?,is_primary=? WHERE id=? AND product_id=?",
                    i, ids.get(i).equals(primary), ids.get(i), productId);
        }
    }
}

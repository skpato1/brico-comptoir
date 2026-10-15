package tn.bricocomptoir.catalog.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.catalog.application.port.out.ProductViewStore;
import tn.bricocomptoir.catalog.domain.ProductViewStats;

@Repository
public class JdbcProductViewStore implements ProductViewStore {
    private static final String VISIBLE = " FROM catalog_product p "
            + "JOIN catalog_category c ON c.id=p.category_id "
            + "LEFT JOIN catalog_brand b ON b.id=p.brand_id "
            + "WHERE p.status='PUBLISHED' AND p.demo=false AND c.active=true "
            + "AND (b.id IS NULL OR b.active=true) "
            + "AND EXISTS (SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.status='PUBLISHED') "
            + "AND (strpos(lower(p.name),lower(?))>0 OR EXISTS "
            + "(SELECT 1 FROM catalog_variant sv WHERE sv.product_id=p.id AND sv.status='PUBLISHED' "
            + "AND strpos(lower(sv.sku),lower(?))>0))";

    private final JdbcTemplate jdbc;

    public JdbcProductViewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void record(UUID productId, LocalDate day) {
        jdbc.update("INSERT INTO catalog_product_view_daily(product_id,day,views) VALUES (?,?,1) "
                + "ON CONFLICT (product_id,day) DO UPDATE SET views=catalog_product_view_daily.views+1",
                productId, day);
    }

    @Override public void deleteBefore(LocalDate firstDayToKeep) {
        jdbc.update("DELETE FROM catalog_product_view_daily WHERE day < ?", firstDayToKeep);
    }

    @Override public ProductViewStats.Page report(LocalDate today, int periodDays, String query, int page, int size) {
        LocalDate start7 = today.minusDays(6);
        LocalDate start30 = today.minusDays(29);
        LocalDate selected = periodDays == 7 ? start7 : start30;
        String search = query.toLowerCase(java.util.Locale.ROOT);
        Long totalElements = jdbc.queryForObject("SELECT count(*)" + VISIBLE, Long.class, search, search);
        Long totalViews = jdbc.queryForObject("SELECT coalesce(sum(d.views),0) FROM (SELECT p.id" + VISIBLE
                + ") visible LEFT JOIN catalog_product_view_daily d ON d.product_id=visible.id "
                + "AND d.day BETWEEN ? AND ?", Long.class, search, search, selected, today);
        Long viewedProducts = jdbc.queryForObject("SELECT count(DISTINCT d.product_id) FROM (SELECT p.id" + VISIBLE
                + ") visible JOIN catalog_product_view_daily d ON d.product_id=visible.id "
                + "AND d.day BETWEEN ? AND ?", Long.class, search, search, selected, today);
        String order = periodDays == 7 ? "views7" : "views30";
        List<ProductViewStats.Row> items = jdbc.query("SELECT p.id, p.name, coalesce(b.name,'') brand_name, "
                + "(SELECT min(sv.sku) FROM catalog_variant sv WHERE sv.product_id=p.id "
                + "AND sv.status='PUBLISHED') sku, "
                + "coalesce(sum(d.views) FILTER (WHERE d.day>=?),0) views7, "
                + "coalesce(sum(d.views),0) views30 "
                + "FROM catalog_product p JOIN catalog_category c ON c.id=p.category_id "
                + "LEFT JOIN catalog_brand b ON b.id=p.brand_id "
                + "LEFT JOIN catalog_product_view_daily d ON d.product_id=p.id AND d.day BETWEEN ? AND ? "
                + "WHERE p.status='PUBLISHED' AND p.demo=false AND c.active=true "
                + "AND (b.id IS NULL OR b.active=true) "
                + "AND EXISTS (SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.status='PUBLISHED') "
                + "AND (strpos(lower(p.name),lower(?))>0 OR EXISTS "
                + "(SELECT 1 FROM catalog_variant sv WHERE sv.product_id=p.id AND sv.status='PUBLISHED' "
                + "AND strpos(lower(sv.sku),lower(?))>0)) "
                + "GROUP BY p.id,p.name,b.name ORDER BY " + order + " DESC, lower(p.name),p.id LIMIT ? OFFSET ?",
                (rs, row) -> new ProductViewStats.Row(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("brand_name"), rs.getString("sku"), rs.getLong("views7"), rs.getLong("views30")),
                start7, start30, today, search, search, size, (long) page * size);
        return new ProductViewStats.Page(items, page, size, totalElements, totalViews, viewedProducts, periodDays);
    }
}

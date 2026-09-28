package tn.bricocomptoir.catalog.adapter.out.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.catalog.application.port.out.CatalogStore;
import tn.bricocomptoir.catalog.domain.CatalogModels.*;

@Repository
public class JdbcCatalogStore implements CatalogStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcCatalogStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final RowMapper<Category> CATEGORY = (rs, row) -> new Category(
            rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class),
            rs.getString("slug"), rs.getString("name"), rs.getBoolean("active"), rs.getLong("version"));
    private static final RowMapper<Brand> BRAND = (rs, row) -> new Brand(
            rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
            rs.getBoolean("active"), rs.getLong("version"));

    @Override public boolean productExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM catalog_product WHERE id=?)", Boolean.class, id));
    }
    @Override public Set<UUID> visibleProductIds(List<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.query("SELECT p.id FROM catalog_product p JOIN catalog_category c ON c.id=p.category_id "
                + "LEFT JOIN catalog_brand b ON b.id=p.brand_id WHERE p.id = ANY(?::uuid[]) "
                + "AND p.status='PUBLISHED' AND c.active=true AND (b.id IS NULL OR b.active=true) "
                + "AND EXISTS (SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.status='PUBLISHED')",
                (rs, row) -> rs.getObject(1, UUID.class),
                jdbc.execute((java.sql.Connection connection) -> connection.createArrayOf("uuid", ids.toArray()))));
    }
    @Override public boolean skuExists(String sku) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM catalog_variant WHERE sku=?)", Boolean.class, sku));
    }

    @Override public List<Category> categories(boolean all) {
        return jdbc.query("SELECT * FROM catalog_category " + (all ? "" : "WHERE active = true ")
                + "ORDER BY name, id", CATEGORY);
    }
    @Override public Page<Category> categoryPage(String q,int page,int size) {
        return referencePage("catalog_category", CATEGORY, q,page,size);
    }
    @Override public Page<Brand> brandPage(String q,int page,int size) {
        return referencePage("catalog_brand", BRAND, q,page,size);
    }
    private <T> Page<T> referencePage(String table,RowMapper<T> mapper,String q,int page,int size) {
        String where=" WHERE strpos(lower(name),lower(?))>0 OR strpos(lower(slug),lower(?))>0";
        long total=jdbc.queryForObject("SELECT count(*) FROM " + table + where,Long.class,q,q);
        return new Page<>(jdbc.query("SELECT * FROM " + table + where + " ORDER BY name,id LIMIT ? OFFSET ?",
                mapper,q,q,size,(long)page*size),page,size,total);
    }
    @Override public Optional<Category> category(UUID id) {
        return jdbc.query("SELECT * FROM catalog_category WHERE id = ?", CATEGORY, id).stream().findFirst();
    }
    @Override public Category saveCategory(Category value, boolean create) {
        jdbc.execute("SELECT pg_advisory_xact_lock(8741001)");
        if (create) {
            jdbc.update("INSERT INTO catalog_category(id,parent_id,slug,name,active) VALUES (?,?,?,?,?)",
                    value.id(), value.parentId(), value.slug(), value.name(), value.active());
        } else if (jdbc.update("UPDATE catalog_category SET parent_id=?,slug=?,name=?,active=?,version=version+1 "
                + "WHERE id=? AND version=?", value.parentId(), value.slug(), value.name(), value.active(),
                value.id(), value.version()) == 0) throw new IllegalStateException("Version conflict");
        return category(value.id()).orElseThrow();
    }
    @Override public boolean categoryHasDependents(UUID id) {
        if (id == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM catalog_category WHERE parent_id=? "
                + "UNION ALL SELECT 1 FROM catalog_product WHERE category_id=?)", Boolean.class, id, id));
    }
    @Override public List<Brand> brands(boolean all) {
        return jdbc.query("SELECT * FROM catalog_brand " + (all ? "" : "WHERE active = true ")
                + "ORDER BY name, id", BRAND);
    }
    @Override public Optional<Brand> brand(UUID id) {
        return jdbc.query("SELECT * FROM catalog_brand WHERE id=?", BRAND, id).stream().findFirst();
    }
    @Override public Brand saveBrand(Brand value, boolean create) {
        if (create) {
            jdbc.update("INSERT INTO catalog_brand(id,slug,name,active) VALUES (?,?,?,?)",
                    value.id(), value.slug(), value.name(), value.active());
        } else if (jdbc.update("UPDATE catalog_brand SET slug=?,name=?,active=?,version=version+1 "
                + "WHERE id=? AND version=?", value.slug(), value.name(), value.active(),
                value.id(), value.version()) == 0) throw new IllegalStateException("Version conflict");
        return brand(value.id()).orElseThrow();
    }
    @Override public boolean brandHasProducts(UUID id) {
        if (id == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM catalog_product WHERE brand_id=?)", Boolean.class, id));
    }

    @Override public Optional<Product> product(UUID id, boolean all) {
        String visibility = all ? "" : " AND p.status='PUBLISHED' AND c.active=true "
                + "AND (b.id IS NULL OR b.active=true) AND EXISTS "
                + "(SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.status='PUBLISHED')";
        return jdbc.query("SELECT p.* FROM catalog_product p JOIN catalog_category c ON c.id=p.category_id "
                + "LEFT JOIN catalog_brand b ON b.id=p.brand_id WHERE p.id=?" + visibility,
                (rs, row) -> mapProduct(rs, all), id).stream().findFirst();
    }

    @Override public Page<Product> products(Search search) {
        StringBuilder where = new StringBuilder(" FROM catalog_product p JOIN catalog_category c ON c.id=p.category_id "
                + "LEFT JOIN catalog_brand b ON b.id=p.brand_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!search.includeDrafts()) where.append(" AND p.status='PUBLISHED' AND c.active=true "
                + "AND (b.id IS NULL OR b.active=true) AND EXISTS "
                + "(SELECT 1 FROM catalog_variant visible WHERE visible.product_id=p.id AND visible.status='PUBLISHED')");
        if (search.categoryId() != null) {
            where.append(" AND p.category_id IN (WITH RECURSIVE children(id) AS "
                    + "(SELECT id FROM catalog_category WHERE id=? UNION ALL "
                    + "SELECT child.id FROM catalog_category child JOIN children ON child.parent_id=children.id) "
                    + "SELECT id FROM children)");
            args.add(search.categoryId());
        }
        if (search.brandId() != null) { where.append(" AND p.brand_id=?"); args.add(search.brandId()); }
        if (search.query() != null) {
            where.append(" AND (lower(p.name) LIKE ? ESCAPE '\\' "
                    + "OR lower(p.description) LIKE ? ESCAPE '\\' "
                    + "OR EXISTS (SELECT 1 FROM catalog_variant qv WHERE qv.product_id=p.id "
                    + (search.includeDrafts() ? "" : "AND qv.status='PUBLISHED' ")
                    + "AND lower(qv.sku) LIKE ? ESCAPE '\\'))");
            String pattern = "%" + search.query().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        if (search.minPrice() != null || search.maxPrice() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM catalog_variant pv WHERE pv.product_id=p.id "
                    + (search.includeDrafts() ? "" : "AND pv.status='PUBLISHED' "));
            if (search.minPrice() != null) { where.append(" AND pv.price_tnd>=?"); args.add(search.minPrice()); }
            if (search.maxPrice() != null) { where.append(" AND pv.price_tnd<=?"); args.add(search.maxPrice()); }
            where.append(')');
        }
        long count = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        String visible = search.includeDrafts() ? "" : " AND v.status='PUBLISHED'";
        String price = "(SELECT min(v.price_tnd) FROM catalog_variant v WHERE v.product_id=p.id" + visible + ")";
        String order = switch (search.sort()) {
            case "price_asc" -> price + " ASC NULLS LAST, p.id";
            case "price_desc" -> price + " DESC NULLS LAST, p.id";
            case "newest" -> "p.created_at DESC, p.id";
            default -> "lower(p.name), p.id";
        };
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(search.size()); pageArgs.add((long) search.page() * search.size());
        List<Product> items = jdbc.query("SELECT p.*" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, row) -> mapProduct(rs, search.includeDrafts()), pageArgs.toArray());
        return new Page<>(items, search.page(), search.size(), count);
    }

    private Product mapProduct(ResultSet rs, boolean all) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new Product(id, rs.getObject("category_id", UUID.class), rs.getObject("brand_id", UUID.class),
                rs.getString("name"), rs.getString("description"), attributes(rs.getString("characteristics")),
                rs.getString("status"), rs.getBoolean("demo"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), variants(id, all));
    }
    private List<Variant> variants(UUID productId, boolean all) {
        return jdbc.query("SELECT * FROM catalog_variant WHERE product_id=? "
                + (all ? "" : "AND status='PUBLISHED' ") + "ORDER BY sku",
                (rs, row) -> mapVariant(rs), productId);
    }
    @Override public Product saveProduct(Product value, boolean create) {
        if (create) {
            jdbc.update("INSERT INTO catalog_product(id,category_id,brand_id,name,description,characteristics,status) "
                    + "VALUES (?,?,?,?,?,?,?)", value.id(), value.categoryId(), value.brandId(), value.name(),
                    value.description(), json(value.characteristics()), value.status());
        } else if (jdbc.update("UPDATE catalog_product SET category_id=?,brand_id=?,name=?,description=?,"
                + "characteristics=?,status=?,version=version+1,updated_at=now() WHERE id=? AND version=?",
                value.categoryId(), value.brandId(), value.name(), value.description(), json(value.characteristics()),
                value.status(), value.id(), value.version()) == 0) throw new IllegalStateException("Version conflict");
        return product(value.id(), true).orElseThrow();
    }
    @Override public Optional<Variant> variant(UUID id) {
        return jdbc.query("SELECT * FROM catalog_variant WHERE id=?", (rs, row) -> mapVariant(rs), id)
                .stream().findFirst();
    }
    private Variant mapVariant(ResultSet rs) throws SQLException {
        return new Variant(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("sku"), rs.getString("label"), rs.getString("unit"),
                attributes(rs.getString("options")), rs.getBigDecimal("price_tnd"),
                rs.getString("status"), rs.getLong("version"));
    }
    @Override public Variant saveVariant(Variant value, boolean create) {
        if (create) {
            jdbc.update("INSERT INTO catalog_variant(id,product_id,sku,label,unit,options,price_tnd,status) "
                    + "VALUES (?,?,?,?,?,?,?,?)", value.id(), value.productId(), value.sku(), value.label(),
                    value.unit(), json(value.options()), value.priceTnd(), value.status());
        } else if (jdbc.update("UPDATE catalog_variant SET sku=?,label=?,unit=?,options=?,price_tnd=?,"
                + "status=?,version=version+1 WHERE id=? AND version=?", value.sku(), value.label(), value.unit(),
                json(value.options()), value.priceTnd(), value.status(), value.id(), value.version()) == 0)
            throw new IllegalStateException("Version conflict");
        jdbc.update("UPDATE catalog_product SET version=version+1,updated_at=now() WHERE id=?", value.productId());
        return variant(value.id()).orElseThrow();
    }
    private Map<String, String> attributes(String value) {
        try { return json.readValue(value, new TypeReference<>() { }); }
        catch (Exception invalid) { throw new IllegalStateException("Stored characteristics are invalid", invalid); }
    }
    private PGobject json(Map<String, String> value) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(json.writeValueAsString(value));
            return object;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid characteristics", invalid);
        }
    }
}

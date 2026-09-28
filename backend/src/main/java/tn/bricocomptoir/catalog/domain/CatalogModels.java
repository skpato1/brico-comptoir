package tn.bricocomptoir.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogModels {
    private CatalogModels() { }

    public record Category(UUID id, UUID parentId, String slug, String name, boolean active, long version) { }
    public record Brand(UUID id, String slug, String name, boolean active, long version) { }
    public record Variant(UUID id, UUID productId, String sku, String label, String unit,
                          Map<String, String> options, BigDecimal priceTnd, String status, long version) { }
    public record Product(UUID id, UUID categoryId, UUID brandId, String name, String description,
                          Map<String, String> characteristics, String status, boolean demo, long version,
                          Instant createdAt, List<Variant> variants) { }
    public record Page<T>(List<T> items, int page, int size, long totalElements) { }
    public record Search(String query, UUID categoryId, UUID brandId, BigDecimal minPrice,
                         BigDecimal maxPrice, String sort, int page, int size, boolean includeDrafts) { }
}

package tn.bricocomptoir.catalog.application.port.in;

import java.util.List;
import java.util.UUID;

public final class CatalogImport {
    private CatalogImport() { }
    public record Row(long line, String productKey, String categorySlug, String brandSlug,
                      String productName, String description, String sku, String variantLabel,
                      String unit, String priceTnd, String status) { }
    public record Issue(long line, String field, String message) { }
    public record Preview(String digest, int rowCount, int productCount, List<Issue> issues, List<Row> sample) { }
    public record Applied(int productCount, int variantCount, List<UUID> productIds) { }
}

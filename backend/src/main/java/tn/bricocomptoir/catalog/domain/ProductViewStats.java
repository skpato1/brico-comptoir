package tn.bricocomptoir.catalog.domain;

import java.util.List;
import java.util.UUID;

public final class ProductViewStats {
    private ProductViewStats() { }

    public record Row(UUID productId, String name, String brandName, String sku,
                      long views7, long views30) { }
    public record Page(List<Row> items, int page, int size, long totalElements,
                       long totalViews, long viewedProducts, int periodDays) { }
}

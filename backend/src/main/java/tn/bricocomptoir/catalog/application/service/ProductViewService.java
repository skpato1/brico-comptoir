package tn.bricocomptoir.catalog.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import tn.bricocomptoir.catalog.application.port.out.ProductViewStore;
import tn.bricocomptoir.catalog.domain.ProductViewStats;

public final class ProductViewService {
    private final CatalogService catalog;
    private final ProductViewStore store;
    private final Clock clock;

    public ProductViewService(CatalogService catalog, ProductViewStore store, Clock clock) {
        this.catalog = catalog;
        this.store = store;
        this.clock = clock;
    }

    public void record(UUID productId) {
        if (productId == null) throw new IllegalArgumentException("Product not found");
        var product = catalog.product(productId, false);
        if (!product.demo()) store.record(productId, LocalDate.now(clock));
    }

    public ProductViewStats.Page report(int periodDays, String query, int page, int size) {
        if (periodDays != 7 && periodDays != 30)
            throw new IllegalArgumentException("Choose a 7 or 30 day period");
        if (page < 0 || page > 100_000 || size < 1 || size > 50
                || query != null && query.length() > 100)
            throw new IllegalArgumentException("Invalid pagination or search query");
        return store.report(LocalDate.now(clock), periodDays, query == null ? "" : query.trim(), page, size);
    }

    public void removeExpiredAggregates() { store.deleteBefore(LocalDate.now(clock).minusDays(29)); }
}

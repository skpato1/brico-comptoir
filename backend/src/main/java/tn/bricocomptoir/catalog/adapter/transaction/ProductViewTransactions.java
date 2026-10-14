package tn.bricocomptoir.catalog.adapter.transaction;

import java.time.Clock;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.catalog.application.port.out.ProductViewStore;
import tn.bricocomptoir.catalog.application.service.CatalogService;
import tn.bricocomptoir.catalog.application.service.ProductViewService;
import tn.bricocomptoir.catalog.domain.ProductViewStats;

@Service
public class ProductViewTransactions {
    private final ProductViewService service;
    public ProductViewTransactions(CatalogService catalog, ProductViewStore store, Clock clock) {
        service = new ProductViewService(catalog, store, clock);
    }
    @Transactional public void record(UUID productId) { service.record(productId); }
    @Transactional(readOnly = true) public ProductViewStats.Page report(int periodDays, String query, int page, int size) {
        return service.report(periodDays, query, page, size);
    }
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional public void removeExpiredAggregates() { service.removeExpiredAggregates(); }
}

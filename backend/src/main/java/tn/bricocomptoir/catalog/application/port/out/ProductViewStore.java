package tn.bricocomptoir.catalog.application.port.out;

import java.time.LocalDate;
import java.util.UUID;
import tn.bricocomptoir.catalog.domain.ProductViewStats;

public interface ProductViewStore {
    void record(UUID productId, LocalDate day);
    ProductViewStats.Page report(LocalDate today, int periodDays, String query, int page, int size);
    void deleteBefore(LocalDate firstDayToKeep);
}

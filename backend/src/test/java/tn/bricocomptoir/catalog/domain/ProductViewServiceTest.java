package tn.bricocomptoir.catalog.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.catalog.application.port.out.CatalogStore;
import tn.bricocomptoir.catalog.application.port.out.ProductViewStore;
import tn.bricocomptoir.catalog.application.service.CatalogService;
import tn.bricocomptoir.catalog.application.service.ProductViewService;
import tn.bricocomptoir.catalog.domain.CatalogModels.Product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductViewServiceTest {
    private final CatalogStore catalogStore = mock(CatalogStore.class);
    private final ProductViewStore views = mock(ProductViewStore.class);
    private final CatalogService catalog = new CatalogService(catalogStore);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
    private final ProductViewService service = new ProductViewService(catalog, views, clock);

    @Test void recordsOnlyVisibleNonDemoProductsOnTheUtcDay() {
        UUID productId = UUID.randomUUID();
        when(catalogStore.product(productId, false)).thenReturn(Optional.of(product(productId, false)));
        service.record(productId);
        verify(views).record(productId, LocalDate.of(2026, 10, 14));
        UUID demoId = UUID.randomUUID();
        when(catalogStore.product(demoId, false)).thenReturn(Optional.of(product(demoId, true)));
        service.record(demoId);
        verifyNoMoreInteractions(views);
        assertThatThrownBy(() -> service.record(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Product not found");
    }

    @Test void validatesTheReportWindowAndPaginationBeforeQueryingStorage() {
        assertThatThrownBy(() -> service.report(8, "", 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(7, "", -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(30, "", 0, 51)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(views);
        when(views.report(LocalDate.of(2026, 10, 14), 7, "SKU", 1, 10))
                .thenReturn(new ProductViewStats.Page(List.of(), 1, 10, 0, 0, 0, 7));
        assertThat(service.report(7, " SKU ", 1, 10).periodDays()).isEqualTo(7);
        service.removeExpiredAggregates();
        verify(views).deleteBefore(LocalDate.of(2026, 9, 15));
    }

    private static Product product(UUID id, boolean demo) {
        return new Product(id, UUID.randomUUID(), null, "Test", "", Map.of(), "PUBLISHED",
                demo, 0, Instant.EPOCH, List.of());
    }
}

package tn.bricocomptoir.catalog.domain;

import java.util.List;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.catalog.application.port.in.CatalogImport.Row;
import tn.bricocomptoir.catalog.application.port.out.CatalogStore;
import tn.bricocomptoir.catalog.application.service.CatalogImportService;
import tn.bricocomptoir.catalog.application.service.CatalogService;
import tn.bricocomptoir.catalog.domain.CatalogModels.Category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogImportServiceTest {
    @Test void previewFindsMissingReferencesDuplicateSkuInconsistentGroupsAndInvalidPrice() {
        CatalogStore store = mock(CatalogStore.class);
        when(store.categories(false)).thenReturn(List.of(new Category(java.util.UUID.randomUUID(), null,
                "fixations", "Fixations", true, 0)));
        when(store.brands(false)).thenReturn(List.of());
        var service = new CatalogImportService(store, new CatalogService(store));
        var preview = service.preview(List.of(
                row(2, "p1", "fixations", "DEMO-1", "2.375", "Exemple"),
                row(3, "p1", "inconnue", "DEMO-1", "0.000", "Nom différent")), "digest");
        assertThat(preview.productCount()).isEqualTo(1);
        assertThat(preview.issues()).extracting(issue -> issue.field())
                .contains("categorySlug", "sku", "priceTnd", "productKey");
    }
    private static Row row(long line, String key, String category, String sku, String price, String name) {
        return new Row(line, key, category, "", name, "", sku, "Format fictif", "pièce", price, "DRAFT");
    }
}

package tn.bricocomptoir.catalog.adapter.transaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.catalog.application.service.CatalogService;
import tn.bricocomptoir.catalog.application.service.CatalogImportService;
import tn.bricocomptoir.catalog.application.port.in.CatalogImport.*;
import tn.bricocomptoir.catalog.domain.CatalogModels.*;

@Service
public class CatalogTransactions {
    private final CatalogService service;
    private final CatalogImportService imports;
    public CatalogTransactions(CatalogService service, CatalogImportService imports) {
        this.service = service; this.imports = imports;
    }
    @Transactional(readOnly = true) public Preview previewImport(List<Row> rows, String digest) {
        return imports.preview(rows, digest);
    }
    @Transactional public Applied applyImport(List<Row> rows, String digest, String expectedDigest) {
        return imports.apply(rows, digest, expectedDigest);
    }

    @Transactional(readOnly = true) public List<Category> categories(boolean admin) { return service.categories(admin); }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<Category> categoryPage(String q,int page,int size) { return service.categoryPage(q,page,size); }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<Brand> brandPage(String q,int page,int size) { return service.brandPage(q,page,size); }
    @Transactional(readOnly = true) public List<Brand> brands(boolean admin) { return service.brands(admin); }
    @Transactional(readOnly = true) public Category category(UUID id) { return service.category(id); }
    @Transactional(readOnly = true) public Brand brand(UUID id) { return service.brand(id); }
    @Transactional(readOnly = true) public Product product(UUID id, boolean admin) { return service.product(id, admin); }
    @Transactional(readOnly = true) public Page<Product> products(String q, UUID category, UUID brand,
            String min, String max, String sort, int page, int size, boolean admin) {
        return service.products(q, category, brand, min, max, sort, page, size, admin);
    }
    @Transactional public Category saveCategory(UUID id, UUID parent, String slug, String name,
            boolean active, Long version) {
        return service.saveCategory(id, parent, slug, name, active, version);
    }
    @Transactional public Brand saveBrand(UUID id, String slug, String name, boolean active, Long version) {
        return service.saveBrand(id, slug, name, active, version);
    }
    @Transactional public Product saveProduct(UUID id, UUID category, UUID brand, String name,
            String description, Map<String, String> characteristics, String status, Long version) {
        return service.saveProduct(id, category, brand, name, description, characteristics, status, version);
    }
    @Transactional public Variant saveVariant(UUID id, UUID product, String sku, String label, String unit,
            Map<String, String> options, String price, String status, Long version) {
        return service.saveVariant(id, product, sku, label, unit, options, price, status, version);
    }
}

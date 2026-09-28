package tn.bricocomptoir.catalog.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tn.bricocomptoir.catalog.application.port.in.CatalogProductQueries;
import tn.bricocomptoir.catalog.application.port.in.CatalogVariantQueries;
import tn.bricocomptoir.catalog.application.port.in.CatalogSaleOfferQueries;
import tn.bricocomptoir.catalog.application.port.out.CatalogStore;
import tn.bricocomptoir.catalog.domain.CatalogModels.*;
import tn.bricocomptoir.catalog.domain.CatalogRules;

public final class CatalogService implements CatalogProductQueries, CatalogVariantQueries, CatalogSaleOfferQueries {
    private final CatalogStore store;
    public CatalogService(CatalogStore store) { this.store = store; }
    @Override public boolean exists(UUID id) { return store.productExists(id); }
    @Override public Set<UUID> visibleIds(List<UUID> ids) { return store.visibleProductIds(ids); }
    @Override public boolean variantExists(UUID variantId) { return store.variant(variantId).isPresent(); }
    @Override public boolean published(UUID variantId) {
        return store.variant(variantId).filter(v -> "PUBLISHED".equals(v.status()))
                .flatMap(v -> store.product(v.productId(), false))
                .filter(p -> "PUBLISHED".equals(p.status())).isPresent();
    }
    @Override public java.util.Optional<CatalogSaleOfferQueries.Offer> offer(UUID variantId) {
        if (variantId == null) return java.util.Optional.empty();
        return store.variant(variantId).filter(v -> "PUBLISHED".equals(v.status()))
                .flatMap(v -> store.product(v.productId(), false)
                        .filter(p -> "PUBLISHED".equals(p.status()))
                        .map(p -> new CatalogSaleOfferQueries.Offer(v.id(), v.version(), p.version(),
                                p.name(), v.sku(), v.label(), v.priceTnd())));
    }

    public List<Category> categories(boolean admin) { return store.categories(admin); }
    public Page<Category> categoryPage(String q, int page, int size) {
        validatePage(q, page, size); return store.categoryPage(q == null ? "" : q.trim(), page, size);
    }
    public Page<Brand> brandPage(String q, int page, int size) {
        validatePage(q, page, size); return store.brandPage(q == null ? "" : q.trim(), page, size);
    }
    private void validatePage(String q, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || q != null && q.length() > 100)
            throw new IllegalArgumentException("Invalid pagination or query");
    }
    public List<Brand> brands(boolean admin) { return store.brands(admin); }
    public Category category(UUID id) {
        return store.category(id).orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }
    public Brand brand(UUID id) {
        return store.brand(id).orElseThrow(() -> new IllegalArgumentException("Brand not found"));
    }
    public Product product(UUID id, boolean admin) {
        return store.product(id, admin).orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }
    public Page<Product> products(String query, UUID category, UUID brand, String min, String max,
                                  String sort, int page, int size, boolean admin) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        if (query != null && query.length() > 100) throw new IllegalArgumentException("Search query too long");
        String selectedSort = sort == null ? "name" : sort;
        if (!List.of("name", "price_asc", "price_desc", "newest").contains(selectedSort))
            throw new IllegalArgumentException("Invalid sort");
        BigDecimal minimum = min == null ? null : CatalogRules.price(min);
        BigDecimal maximum = max == null ? null : CatalogRules.price(max);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Invalid price range");
        return store.products(new Search(query == null || query.isBlank() ? null : query.trim(),
                category, brand, minimum, maximum, selectedSort, page, size, admin));
    }

    public Category saveCategory(UUID id, UUID parentId, String slug, String name, boolean active,
                                 Long expectedVersion) {
        boolean create = id == null;
        Category prior = create ? null : store.category(id).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (!create) checkVersion(prior.version(), expectedVersion);
        if (parentId != null) {
            if (parentId.equals(id)) throw new IllegalArgumentException("Category cannot parent itself");
            Category parent = store.category(parentId).orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            if (!parent.active()) throw new IllegalStateException("Parent category is inactive");
        }
        if (!active && store.categoryHasDependents(id)) throw new IllegalStateException("Category has dependents");
        return store.saveCategory(new Category(create ? UUID.randomUUID() : id, parentId,
                CatalogRules.slug(slug), CatalogRules.text(name, 120, "category name"), active,
                create ? 0 : prior.version()), create);
    }

    public Brand saveBrand(UUID id, String slug, String name, boolean active, Long expectedVersion) {
        boolean create = id == null;
        Brand prior = create ? null : store.brand(id).orElseThrow(() -> new IllegalArgumentException("Brand not found"));
        if (!create) checkVersion(prior.version(), expectedVersion);
        if (!active && store.brandHasProducts(id)) throw new IllegalStateException("Brand has products");
        return store.saveBrand(new Brand(create ? UUID.randomUUID() : id, CatalogRules.slug(slug),
                CatalogRules.text(name, 120, "brand name"), active, create ? 0 : prior.version()), create);
    }

    public Product saveProduct(UUID id, UUID categoryId, UUID brandId, String name, String description,
                               Map<String, String> characteristics, String status, Long expectedVersion) {
        boolean create = id == null;
        Product prior = create ? null : product(id, true);
        if (!create) checkVersion(prior.version(), expectedVersion);
        Category category = store.category(categoryId).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (!category.active()) throw new IllegalStateException("Category is inactive");
        if (brandId != null && !store.brand(brandId).filter(Brand::active).isPresent())
            throw new IllegalArgumentException("Brand not found or inactive");
        Product product = new Product(create ? UUID.randomUUID() : id, categoryId, brandId,
                CatalogRules.text(name, 180, "product name"),
                CatalogRules.optionalText(description, 2000, "description"),
                CatalogRules.attributes(characteristics), CatalogRules.status(status),
                !create && prior.demo(), create ? 0 : prior.version(), create ? null : prior.createdAt(), List.of());
        return store.saveProduct(product, create);
    }

    public Variant saveVariant(UUID id, UUID productId, String sku, String label, String unit,
                               Map<String, String> options, String price, String status, Long expectedVersion) {
        boolean create = id == null;
        Product product = product(productId, true);
        Variant prior = create ? null : store.variant(id).orElseThrow(() -> new IllegalArgumentException("Variant not found"));
        if (!create) {
            checkVersion(prior.version(), expectedVersion);
            if (!prior.productId().equals(productId)) throw new IllegalArgumentException("Variant cannot change product");
        }
        return store.saveVariant(new Variant(create ? UUID.randomUUID() : id, product.id(),
                CatalogRules.sku(sku), CatalogRules.text(label, 120, "variant label"),
                CatalogRules.text(unit, 40, "unit"), CatalogRules.attributes(options),
                CatalogRules.price(price), CatalogRules.status(status), create ? 0 : prior.version()), create);
    }

    private void checkVersion(long actual, Long expected) {
        if (expected == null || expected < 0) throw new IllegalArgumentException("Expected version required");
        if (actual != expected) throw new IllegalStateException("Version conflict");
    }
}

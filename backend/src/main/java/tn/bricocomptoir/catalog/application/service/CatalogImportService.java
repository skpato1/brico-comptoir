package tn.bricocomptoir.catalog.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tn.bricocomptoir.catalog.application.port.in.CatalogImport.*;
import tn.bricocomptoir.catalog.application.port.out.CatalogStore;
import tn.bricocomptoir.catalog.domain.CatalogModels.*;
import tn.bricocomptoir.catalog.domain.CatalogRules;

public final class CatalogImportService {
    private final CatalogStore store;
    private final CatalogService catalog;
    public CatalogImportService(CatalogStore store, CatalogService catalog) {
        this.store = store; this.catalog = catalog;
    }

    public Preview preview(List<Row> rows, String digest) {
        List<Issue> issues = new ArrayList<>();
        if (rows.isEmpty() || rows.size() > 500) issues.add(new Issue(0, "file", "CSV must contain 1–500 rows"));
        Map<String, Category> categories = new HashMap<>();
        store.categories(false).forEach(category -> categories.put(category.slug(), category));
        Map<String, Brand> brands = new HashMap<>();
        store.brands(false).forEach(brand -> brands.put(brand.slug(), brand));
        Map<String, Row> products = new LinkedHashMap<>();
        Set<String> skus = new HashSet<>();
        for (Row row : rows) {
            validate(row, categories, brands, products, skus, issues);
        }
        return new Preview(digest, rows.size(), products.size(), List.copyOf(issues),
                rows.stream().limit(20).toList());
    }

    private void validate(Row row, Map<String, Category> categories, Map<String, Brand> brands,
                          Map<String, Row> products, Set<String> skus, List<Issue> issues) {
        check(row, "productKey", () -> {
            if (row.productKey() == null || !row.productKey().matches("[a-z0-9][a-z0-9-]{0,79}"))
                throw new IllegalArgumentException("Invalid product key");
        }, issues);
        check(row, "categorySlug", () -> {
            CatalogRules.slug(row.categorySlug());
            if (!categories.containsKey(row.categorySlug())) throw new IllegalArgumentException("Category not found or inactive");
        }, issues);
        check(row, "brandSlug", () -> {
            if (row.brandSlug() != null && !row.brandSlug().isEmpty()) {
                CatalogRules.slug(row.brandSlug());
                if (!brands.containsKey(row.brandSlug())) throw new IllegalArgumentException("Brand not found or inactive");
            }
        }, issues);
        check(row, "productName", () -> CatalogRules.text(row.productName(), 180, "product name"), issues);
        check(row, "description", () -> CatalogRules.optionalText(row.description(), 2000, "description"), issues);
        check(row, "sku", () -> {
            CatalogRules.sku(row.sku());
            if (!skus.add(row.sku()) || store.skuExists(row.sku()))
                throw new IllegalArgumentException("SKU already exists");
        }, issues);
        check(row, "variantLabel", () -> CatalogRules.text(row.variantLabel(), 120, "variant label"), issues);
        check(row, "unit", () -> CatalogRules.text(row.unit(), 40, "unit"), issues);
        check(row, "priceTnd", () -> CatalogRules.price(row.priceTnd()), issues);
        check(row, "status", () -> CatalogRules.status(row.status()), issues);
        Row first = products.putIfAbsent(row.productKey(), row);
        if (first != null && !(same(first.categorySlug(), row.categorySlug())
                && same(first.brandSlug(), row.brandSlug()) && same(first.productName(), row.productName())
                && same(first.description(), row.description()) && same(first.status(), row.status())))
            issues.add(new Issue(row.line(), "productKey", "Product fields differ between rows for the same key"));
    }

    private static boolean same(String left, String right) { return java.util.Objects.equals(left, right); }
    private static void check(Row row, String field, Runnable validation, List<Issue> issues) {
        try { validation.run(); }
        catch (IllegalArgumentException invalid) { issues.add(new Issue(row.line(), field, invalid.getMessage())); }
    }

    public Applied apply(List<Row> rows, String digest, String expectedDigest) {
        if (expectedDigest == null || !digest.equals(expectedDigest))
            throw new IllegalArgumentException("CSV digest differs from preview");
        Preview preview = preview(rows, digest);
        if (!preview.issues().isEmpty()) throw new IllegalArgumentException("CSV has validation errors; preview again");
        Map<String, UUID> created = new LinkedHashMap<>();
        Map<String, Category> categories = new HashMap<>();
        store.categories(false).forEach(category -> categories.put(category.slug(), category));
        Map<String, Brand> brands = new HashMap<>();
        store.brands(false).forEach(brand -> brands.put(brand.slug(), brand));
        for (Row row : rows) {
            UUID productId = created.get(row.productKey());
            if (productId == null) {
                Brand brand = brands.get(row.brandSlug());
                Product product = catalog.saveProduct(null, categories.get(row.categorySlug()).id(),
                        brand == null ? null : brand.id(), row.productName(), row.description(), Map.of(),
                        row.status(), null);
                productId = product.id();
                created.put(row.productKey(), productId);
            }
            catalog.saveVariant(null, productId, row.sku(), row.variantLabel(), row.unit(), Map.of(),
                    row.priceTnd(), row.status(), null);
        }
        return new Applied(created.size(), rows.size(), List.copyOf(created.values()));
    }
}

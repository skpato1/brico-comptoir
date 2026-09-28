package tn.bricocomptoir.catalog.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.catalog.domain.CatalogModels.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogTransactions catalog;
    public CatalogController(CatalogTransactions catalog) { this.catalog = catalog; }

    @GetMapping("/categories")
    public List<Category> categories() { return catalog.categories(false); }
    @GetMapping("/brands")
    public List<Brand> brands() { return catalog.brands(false); }
    @GetMapping("/products")
    public Page<ProductView> products(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) String minPrice, @RequestParam(required = false) String maxPrice,
            @RequestParam(defaultValue = "name") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return viewPage(catalog.products(q, categoryId, brandId, minPrice, maxPrice, sort, page, size, false));
    }
    @GetMapping("/products/{id}")
    public ProductView product(@PathVariable UUID id) { return ProductView.of(catalog.product(id, false)); }

    @GetMapping("/admin/catalog/categories/page")
    public ResponseEntity<Page<Category>> categoryPage(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.categoryPage(q,page,size));
    }
    @GetMapping("/admin/catalog/brands/page")
    public ResponseEntity<Page<Brand>> brandPage(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.brandPage(q,page,size));
    }
    @GetMapping("/admin/catalog/categories")
    public ResponseEntity<List<Category>> adminCategories() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.categories(true));
    }
    @GetMapping("/admin/catalog/brands")
    public ResponseEntity<List<Brand>> adminBrands() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.brands(true));
    }
    @GetMapping("/admin/catalog/categories/{id}")
    public ResponseEntity<Category> adminCategory(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.category(id));
    }
    @GetMapping("/admin/catalog/brands/{id}")
    public ResponseEntity<Brand> adminBrand(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.brand(id));
    }
    @GetMapping("/admin/catalog/products")
    public ResponseEntity<Page<ProductView>> adminProducts(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) String minPrice, @RequestParam(required = false) String maxPrice,
            @RequestParam(defaultValue = "name") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(viewPage(
                catalog.products(q, categoryId, brandId, minPrice, maxPrice, sort, page, size, true)));
    }
    @GetMapping("/admin/catalog/products/{id}")
    public ResponseEntity<ProductView> adminProduct(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ProductView.of(catalog.product(id, true)));
    }

    @PostMapping("/admin/catalog/categories")
    public ResponseEntity<Category> createCategory(@Valid @RequestBody CategoryInput input) {
        Category item = catalog.saveCategory(null, input.parentId(), input.slug(), input.name(), input.active(), null);
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/categories/" + item.id()))
                .cacheControl(CacheControl.noStore()).body(item);
    }
    @PutMapping("/admin/catalog/categories/{id}")
    public Category updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryInput input) {
        return catalog.saveCategory(id, input.parentId(), input.slug(), input.name(), input.active(), input.version());
    }
    @PostMapping("/admin/catalog/brands")
    public ResponseEntity<Brand> createBrand(@Valid @RequestBody BrandInput input) {
        Brand item = catalog.saveBrand(null, input.slug(), input.name(), input.active(), null);
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/brands/" + item.id()))
                .cacheControl(CacheControl.noStore()).body(item);
    }
    @PutMapping("/admin/catalog/brands/{id}")
    public Brand updateBrand(@PathVariable UUID id, @Valid @RequestBody BrandInput input) {
        return catalog.saveBrand(id, input.slug(), input.name(), input.active(), input.version());
    }
    @PostMapping("/admin/catalog/products")
    public ResponseEntity<ProductView> createProduct(@Valid @RequestBody ProductInput input) {
        Product item = catalog.saveProduct(null, input.categoryId(), input.brandId(), input.name(),
                input.description(), input.characteristics(), input.status(), null);
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/products/" + item.id()))
                .cacheControl(CacheControl.noStore()).body(ProductView.of(item));
    }
    @PutMapping("/admin/catalog/products/{id}")
    public ProductView updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductInput input) {
        return ProductView.of(catalog.saveProduct(id, input.categoryId(), input.brandId(), input.name(),
                input.description(), input.characteristics(), input.status(), input.version()));
    }
    @PostMapping("/admin/catalog/products/{productId}/variants")
    public ResponseEntity<VariantView> createVariant(@PathVariable UUID productId,
            @Valid @RequestBody VariantInput input) {
        Variant item = catalog.saveVariant(null, productId, input.sku(), input.label(), input.unit(),
                input.options(), input.priceTnd(), input.status(), null);
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/products/" + productId
                + "/variants/" + item.id())).cacheControl(CacheControl.noStore()).body(VariantView.of(item));
    }
    @PutMapping("/admin/catalog/products/{productId}/variants/{id}")
    public VariantView updateVariant(@PathVariable UUID productId, @PathVariable UUID id,
            @Valid @RequestBody VariantInput input) {
        return VariantView.of(catalog.saveVariant(id, productId, input.sku(), input.label(), input.unit(),
                input.options(), input.priceTnd(), input.status(), input.version()));
    }

    private Page<ProductView> viewPage(Page<Product> page) {
        return new Page<>(page.items().stream().map(ProductView::of).toList(),
                page.page(), page.size(), page.totalElements());
    }
    public record CategoryInput(UUID parentId, @NotBlank String slug, @NotBlank String name,
                                @NotNull Boolean active, Long version) { }
    public record BrandInput(@NotBlank String slug, @NotBlank String name,
                             @NotNull Boolean active, Long version) { }
    public record ProductInput(@NotNull UUID categoryId, UUID brandId, @NotBlank String name,
                               String description, Map<String, String> characteristics,
                               @NotBlank String status, Long version) { }
    public record VariantInput(@NotBlank String sku, @NotBlank String label, @NotBlank String unit,
                               Map<String, String> options, @NotBlank String priceTnd,
                               @NotBlank String status, Long version) { }
    public record MoneyView(String amount, String currency) { }
    public record VariantView(UUID id, String sku, String label, String unit, Map<String, String> options,
                              MoneyView price, String status, long version) {
        static VariantView of(Variant variant) {
            return new VariantView(variant.id(), variant.sku(), variant.label(), variant.unit(),
                    variant.options(), new MoneyView(variant.priceTnd().toPlainString(), "TND"),
                    variant.status(), variant.version());
        }
    }
    public record ProductView(UUID id, UUID categoryId, UUID brandId, String name, String description,
                              Map<String, String> characteristics, String status, boolean demo,
                              long version, List<VariantView> variants) {
        static ProductView of(Product product) {
            return new ProductView(product.id(), product.categoryId(), product.brandId(), product.name(),
                    product.description(), product.characteristics(), product.status(), product.demo(),
                    product.version(), product.variants().stream().map(VariantView::of).toList());
        }
    }
}

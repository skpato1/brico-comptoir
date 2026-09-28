package tn.bricocomptoir.catalog.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tn.bricocomptoir.catalog.domain.CatalogModels.*;

public interface CatalogStore {
    List<Category> categories(boolean includeInactive);
    Page<Category> categoryPage(String query, int page, int size);
    Page<Brand> brandPage(String query, int page, int size);
    Optional<Category> category(UUID id);
    Category saveCategory(Category category, boolean create);
    boolean categoryHasDependents(UUID id);
    List<Brand> brands(boolean includeInactive);
    Optional<Brand> brand(UUID id);
    Brand saveBrand(Brand brand, boolean create);
    boolean brandHasProducts(UUID id);
    Optional<Product> product(UUID id, boolean includeDrafts);
    Page<Product> products(Search search);
    Product saveProduct(Product product, boolean create);
    Optional<Variant> variant(UUID id);
    Variant saveVariant(Variant variant, boolean create);
    boolean productExists(UUID id);
    Set<UUID> visibleProductIds(List<UUID> ids);
    boolean skuExists(String sku);
}

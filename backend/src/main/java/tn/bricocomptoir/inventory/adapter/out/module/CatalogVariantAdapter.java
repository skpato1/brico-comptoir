package tn.bricocomptoir.inventory.adapter.out.module;

import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.catalog.application.port.in.CatalogVariantQueries;
import tn.bricocomptoir.inventory.application.port.out.VariantReferencePort;

@Component
public final class CatalogVariantAdapter implements VariantReferencePort {
    private final CatalogVariantQueries catalog;
    public CatalogVariantAdapter(CatalogVariantQueries catalog) { this.catalog = catalog; }
    @Override public boolean exists(UUID variantId) { return catalog.variantExists(variantId); }
    @Override public boolean published(UUID variantId) { return catalog.published(variantId); }
}

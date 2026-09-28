package tn.bricocomptoir.packs.adapter.out.module;

import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.catalog.application.port.in.CatalogVariantQueries;
import tn.bricocomptoir.catalog.application.port.in.CatalogSaleOfferQueries;
import tn.bricocomptoir.packs.application.port.out.CatalogSkuLookup;

@Component
public class CatalogSkuAdapter implements CatalogSkuLookup {
    private final CatalogVariantQueries catalog;
    private final CatalogSaleOfferQueries offers;
    public CatalogSkuAdapter(CatalogVariantQueries catalog, CatalogSaleOfferQueries offers) {
        this.catalog = catalog; this.offers = offers;
    }
    @Override public boolean exists(UUID id) { return catalog.variantExists(id); }
    @Override public boolean published(UUID id) { return catalog.published(id); }
    @Override public String displayName(UUID id) {
        return offers.offer(id).map(o -> o.name() + " · " + o.label() + " (" + o.sku() + ")")
                .orElse("Article non publié");
    }
}

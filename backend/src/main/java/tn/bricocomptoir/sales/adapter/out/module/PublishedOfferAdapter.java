package tn.bricocomptoir.sales.adapter.out.module;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.catalog.application.port.in.CatalogSaleOfferQueries;
import tn.bricocomptoir.packs.application.port.in.PackOfferQueries;
import tn.bricocomptoir.sales.application.port.out.OfferLookup;
import tn.bricocomptoir.sales.domain.CartModels.SkuRequirement;
import tn.bricocomptoir.sales.domain.CartModels.Kind;

@Component
public class PublishedOfferAdapter implements OfferLookup {
    private final CatalogSaleOfferQueries catalog;
    private final PackOfferQueries packs;
    public PublishedOfferAdapter(CatalogSaleOfferQueries catalog, PackOfferQueries packs) {
        this.catalog = catalog; this.packs = packs;
    }

    @Override public Optional<Offer> published(Kind kind, UUID offerId) {
        if (kind == Kind.PRODUCT) return catalog.offer(offerId).map(offer -> new Offer(
                offer.name() + " · " + offer.label() + " · " + offer.sku(), offer.priceTnd(),
                offer.version(), offer.productVersion(), List.of(new SkuRequirement(offerId, 1))));
        return packs.offer(offerId).map(offer -> new Offer(offer.packName() + " · " + offer.variantLabel(),
                offer.priceTnd(), offer.variantVersion(), offer.packVersion(),
                offer.components().stream().map(c -> new SkuRequirement(c.variantId(), c.quantity())).toList()));
    }
}

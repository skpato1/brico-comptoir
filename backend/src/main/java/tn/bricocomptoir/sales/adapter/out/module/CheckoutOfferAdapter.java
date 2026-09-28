package tn.bricocomptoir.sales.adapter.out.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.catalog.application.port.in.CatalogSaleOfferQueries;
import tn.bricocomptoir.packs.application.port.in.PackOfferQueries;
import tn.bricocomptoir.sales.application.port.out.CheckoutOffers;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.SkuSnapshot;

@Component
public class CheckoutOfferAdapter implements CheckoutOffers {
    private final CatalogSaleOfferQueries catalog;
    private final PackOfferQueries packs;
    public CheckoutOfferAdapter(CatalogSaleOfferQueries catalog, PackOfferQueries packs) {
        this.catalog = catalog; this.packs = packs;
    }
    @Override public Optional<Offer> published(Kind kind, UUID id) {
        if (kind == Kind.PRODUCT) return catalog.offer(id).map(o -> new Offer(o.name() + " · " + o.label(),
                o.priceTnd(), o.version(), o.productVersion(), List.of(component(o, 1))));
        var found = packs.offer(id);
        if (found.isEmpty()) return Optional.empty();
        var pack = found.get();
        List<SkuSnapshot> components = new ArrayList<>();
        for (var c : pack.components().stream().sorted(java.util.Comparator.comparing(v -> v.variantId().toString())).toList()) {
            var sku = catalog.offer(c.variantId());
            if (sku.isEmpty()) return Optional.empty();
            components.add(component(sku.get(), c.quantity()));
        }
        return Optional.of(new Offer(pack.packName() + " · " + pack.variantLabel(), pack.priceTnd(),
                pack.variantVersion(), pack.packVersion(), List.copyOf(components)));
    }
    private SkuSnapshot component(CatalogSaleOfferQueries.Offer o, long quantity) {
        return new SkuSnapshot(o.variantId(), o.sku(), o.name() + " · " + o.label(), quantity, o.version(), o.productVersion());
    }
}

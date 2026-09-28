package tn.bricocomptoir.catalog.application.port.in;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Published SKU offer snapshot for another module, without exposing catalog persistence. */
public interface CatalogSaleOfferQueries {
    Optional<Offer> offer(UUID variantId);

    record Offer(UUID variantId, long version, long productVersion, String name, String sku, String label,
                 BigDecimal priceTnd) { }
}

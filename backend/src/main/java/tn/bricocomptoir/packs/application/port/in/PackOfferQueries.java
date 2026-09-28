package tn.bricocomptoir.packs.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable pack offer for the future sales module; no stock is reserved here. */
public interface PackOfferQueries {
    Optional<Offer> offer(UUID packVariantId);

    record SkuRequirement(UUID variantId, long quantity) { }
    record Offer(UUID packId, long packVersion, UUID variantId, long variantVersion,
                 String packName, String variantLabel, BigDecimal priceTnd,
                 List<SkuRequirement> components) { }
}

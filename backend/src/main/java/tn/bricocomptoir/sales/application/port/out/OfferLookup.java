package tn.bricocomptoir.sales.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.sales.domain.CartModels.SkuRequirement;
import tn.bricocomptoir.sales.domain.CartModels.Kind;

public interface OfferLookup {
    Optional<Offer> published(Kind kind, UUID offerId);

    record Offer(String label, BigDecimal priceTnd, long version, long parentVersion,
                 List<SkuRequirement> components) { }
}

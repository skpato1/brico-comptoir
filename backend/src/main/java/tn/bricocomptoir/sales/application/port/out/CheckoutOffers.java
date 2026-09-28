package tn.bricocomptoir.sales.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.SkuSnapshot;

public interface CheckoutOffers {
    Optional<Offer> published(Kind kind, UUID offerId);
    record Offer(String label, BigDecimal priceTnd, long version, long parentVersion,
                 List<SkuSnapshot> components) { }
}

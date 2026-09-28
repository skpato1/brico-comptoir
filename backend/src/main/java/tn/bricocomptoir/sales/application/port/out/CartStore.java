package tn.bricocomptoir.sales.application.port.out;

import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.sales.domain.CartModels.Cart;

public interface CartStore {
    Optional<Cart> read(UUID customerId);
    Cart lock(UUID customerId);
    Cart save(Cart cart);
    Optional<String> mergeFingerprint(UUID customerId, UUID mergeId);
    void recordMerge(UUID customerId, UUID mergeId, String fingerprint);
}

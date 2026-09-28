package tn.bricocomptoir.packs.application.port.out;

import java.util.UUID;

public interface StockAvailabilityLookup {
    long available(UUID variantId);
}

package tn.bricocomptoir.packs.application.port.out;

import java.util.UUID;

public interface CatalogSkuLookup {
    boolean exists(UUID variantId);
    boolean published(UUID variantId);
    String displayName(UUID variantId);
}

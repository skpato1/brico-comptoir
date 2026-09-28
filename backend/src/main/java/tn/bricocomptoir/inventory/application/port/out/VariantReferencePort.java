package tn.bricocomptoir.inventory.application.port.out;

import java.util.UUID;

public interface VariantReferencePort {
    boolean exists(UUID variantId);
    boolean published(UUID variantId);
}

package tn.bricocomptoir.catalog.application.port.in;

import java.util.UUID;

/** Public contract for modules referring to a catalogue SKU by its stable variant ID. */
public interface CatalogVariantQueries {
    boolean variantExists(UUID variantId);
    boolean published(UUID variantId);
}

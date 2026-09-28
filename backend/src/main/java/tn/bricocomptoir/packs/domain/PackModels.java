package tn.bricocomptoir.packs.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class PackModels {
    private PackModels() { }
    public record Page<T>(List<T> items,int page,int size,long totalElements) { }

    public record Component(UUID variantId, long quantity) { }

    public record PackVariant(UUID id, UUID packId, String code, String label,
                              BigDecimal priceTnd, String status, long version,
                              List<Component> components) { }

    public record Pack(UUID id, String code, String name, String slogan, String guide,
                       String status, boolean demo, long version, List<PackVariant> variants) { }
}

package tn.bricocomptoir.packs.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.packs.application.port.in.PackOfferQueries;
import tn.bricocomptoir.packs.application.port.out.CatalogSkuLookup;
import tn.bricocomptoir.packs.application.port.out.PackStore;
import tn.bricocomptoir.packs.application.port.out.StockAvailabilityLookup;
import tn.bricocomptoir.packs.domain.PackModels.*;
import tn.bricocomptoir.packs.domain.PackRules;

public final class PackService implements PackOfferQueries {
    public Page<Pack> page(String q,int page,int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || q != null && q.length() > 100)
            throw new IllegalArgumentException("Invalid pagination or query");
        return store.page(q == null ? "" : q.trim(),page,size);
    }
    private final PackStore store;
    private final CatalogSkuLookup catalog;
    private final StockAvailabilityLookup stock;

    public PackService(PackStore store, CatalogSkuLookup catalog, StockAvailabilityLookup stock) {
        this.store = store; this.catalog = catalog; this.stock = stock;
    }

    public List<Pack> packs(boolean admin) {
        List<Pack> result = new ArrayList<>();
        for (Pack pack : store.packs()) {
            Pack visible = admin ? pack : publicPack(pack);
            if (visible != null) result.add(visible);
        }
        return List.copyOf(result);
    }

    public Pack pack(UUID id, boolean admin) {
        Pack found = store.pack(id).orElseThrow(() -> new IllegalArgumentException("Pack not found"));
        Pack visible = admin ? found : publicPack(found);
        if (visible == null) throw new IllegalArgumentException("Pack not found");
        return visible;
    }

    public Map<UUID, Long> availability(Pack pack) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (PackVariant variant : pack.variants()) result.put(variant.id(), availability(variant));
        return Map.copyOf(result);
    }

    public Map<UUID, String> componentNames(Pack pack) {
        Map<UUID, String> names = new LinkedHashMap<>();
        pack.variants().stream().flatMap(v -> v.components().stream()).map(Component::variantId)
                .distinct().forEach(id -> names.put(id, catalog.displayName(id)));
        return Map.copyOf(names);
    }

    public long availability(PackVariant variant) {
        Map<UUID, Long> available = new LinkedHashMap<>();
        for (Component component : variant.components()) {
            available.put(component.variantId(), catalog.published(component.variantId())
                    ? stock.available(component.variantId()) : 0L);
        }
        return PackRules.availability(variant.components(), available);
    }

    @Override public Optional<Offer> offer(UUID packVariantId) {
        if (packVariantId == null) return Optional.empty();
        return store.variant(packVariantId).flatMap(variant -> store.pack(variant.packId())
                .filter(pack -> "PUBLISHED".equals(pack.status()) && visible(variant))
                .map(pack -> new Offer(pack.id(), pack.version(), variant.id(), variant.version(),
                        pack.name(), variant.label(), variant.priceTnd(), variant.components().stream()
                        .map(c -> new PackOfferQueries.SkuRequirement(c.variantId(), c.quantity())).toList())));
    }

    public Pack savePack(UUID id, String code, String name, String slogan, String guide,
                         String status, Long expectedVersion) {
        boolean create = id == null;
        Pack prior = create ? null : pack(id, true);
        if (!create) checkVersion(prior.version(), expectedVersion);
        String validStatus = PackRules.status(status);
        if (!create && prior.demo() && "PUBLISHED".equals(validStatus))
            throw new IllegalStateException("Demo pack cannot be published without validated data");
        if ("PUBLISHED".equals(validStatus) && (create || prior.variants().stream().noneMatch(this::visible)))
            throw new IllegalStateException("Pack needs a published variant with visible components");
        return store.savePack(new Pack(create ? UUID.randomUUID() : id,
                PackRules.code(code), PackRules.text(name, 180, "pack name"),
                PackRules.optionalText(slogan, 180, "slogan"),
                PackRules.optionalText(guide, 4000, "guide"), validStatus,
                !create && prior.demo(), create ? 0 : prior.version(), List.of()), create);
    }

    public PackVariant saveVariant(UUID id, UUID packId, String code, String label, String priceTnd,
                                   String status, List<Component> components, Long expectedVersion) {
        Pack pack = pack(packId, true);
        boolean create = id == null;
        PackVariant prior = create ? null : store.variant(id)
                .orElseThrow(() -> new IllegalArgumentException("Pack variant not found"));
        if (!create) {
            checkVersion(prior.version(), expectedVersion);
            if (!prior.packId().equals(packId)) throw new IllegalArgumentException("Variant cannot change pack");
        }
        List<Component> valid = PackRules.normalize(components);
        for (Component component : valid) {
            if (!catalog.exists(component.variantId())) throw new IllegalArgumentException("SKU not found");
        }
        String validStatus = PackRules.status(status);
        if ("PUBLISHED".equals(validStatus) && !allComponentsPublished(valid))
            throw new IllegalStateException("Pack component is not published");
        if ("PUBLISHED".equals(pack.status()) && "DRAFT".equals(validStatus)
                && !pack.variants().stream().filter(v -> !v.id().equals(id)).anyMatch(this::visible))
            throw new IllegalStateException("Published pack needs a visible variant");
        return store.saveVariant(new PackVariant(create ? UUID.randomUUID() : id, packId,
                PackRules.code(code), PackRules.text(label, 120, "pack variant label"),
                PackRules.price(priceTnd), validStatus, create ? 0 : prior.version(), valid), create);
    }

    private Pack publicPack(Pack pack) {
        if (!"PUBLISHED".equals(pack.status())) return null;
        List<PackVariant> visible = pack.variants().stream().filter(this::visible).toList();
        if (visible.isEmpty()) return null;
        return new Pack(pack.id(), pack.code(), pack.name(), pack.slogan(), pack.guide(),
                pack.status(), pack.demo(), pack.version(), visible);
    }

    private boolean visible(PackVariant variant) {
        return "PUBLISHED".equals(variant.status()) && allComponentsPublished(variant.components());
    }

    private boolean allComponentsPublished(List<Component> components) {
        return !components.isEmpty() && components.stream().allMatch(c -> catalog.published(c.variantId()));
    }

    private void checkVersion(long actual, Long expected) {
        if (expected == null || expected < 0) throw new IllegalArgumentException("Expected version required");
        if (actual != expected) throw new IllegalStateException("Version conflict");
    }
}

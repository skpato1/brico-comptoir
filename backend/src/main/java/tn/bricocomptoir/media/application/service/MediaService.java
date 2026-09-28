package tn.bricocomptoir.media.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import tn.bricocomptoir.media.application.port.out.CatalogProductLookup;
import tn.bricocomptoir.media.application.port.out.ImageProcessor;
import tn.bricocomptoir.media.application.port.out.MediaStore;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import tn.bricocomptoir.media.domain.ImageRules;
import tn.bricocomptoir.media.domain.ProductImage;

public final class MediaService {
    public record Upload(byte[] bytes, String declaredType) { }
    public record Rendition(byte[] bytes, String type) { }
    private final CatalogProductLookup catalog;
    private final ImageProcessor processor;
    private final MediaStore store;
    private final ObjectStorage objects;

    public MediaService(CatalogProductLookup catalog, ImageProcessor processor, MediaStore store, ObjectStorage objects) {
        this.catalog = catalog; this.processor = processor; this.store = store; this.objects = objects;
    }

    public List<ProductImage> upload(UUID productId, List<Upload> uploads) {
        if (!catalog.exists(productId)) throw new IllegalArgumentException("Product not found");
        store.lockProduct(productId);
        List<ProductImage> prior = store.images(productId);
        ImageRules.capacity(prior.size(), uploads.size());
        // Validate the entire batch before writing the first object.
        var prepared = uploads.stream().map(upload -> processor.process(upload.bytes(), upload.declaredType())).toList();
        List<String> written = new ArrayList<>();
        try {
            for (int i = 0; i < uploads.size(); i++) {
                Upload upload = uploads.get(i);
                ImageProcessor.Processed image = prepared.get(i);
                UUID id = UUID.randomUUID();
                String prefix = "products/" + productId + "/" + id + "/";
                String original = prefix + "original." + (image.sourceMime().equals("image/png") ? "png" : "jpg");
                String card = prefix + "card.jpg";
                String detail = prefix + "detail.jpg";
                put(written, original, upload.bytes(), image.sourceMime());
                put(written, card, image.card(), "image/jpeg");
                put(written, detail, image.detail(), "image/jpeg");
                store.insert(new ProductImage(id, productId, original, card, detail, image.sourceMime(),
                        upload.bytes().length, digest(upload.bytes()), image.width(), image.height(),
                        prior.size() + i, prior.isEmpty() && i == 0, null));
            }
            return store.images(productId);
        } catch (RuntimeException failure) {
            written.forEach(key -> { try { objects.delete(key); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); } });
            throw failure;
        }
    }

    public List<ProductImage> adminImages(UUID productId) {
        if (!catalog.exists(productId)) throw new IllegalArgumentException("Product not found");
        return store.images(productId);
    }

    public List<ProductImage> reorder(UUID productId, List<UUID> ids, UUID primary) {
        if (!catalog.exists(productId)) throw new IllegalArgumentException("Product not found");
        store.lockProduct(productId);
        ImageRules.order(store.images(productId), ids, primary);
        store.reorder(productId, ids, primary);
        return store.images(productId);
    }

    public Map<UUID, List<ProductImage>> publicImages(List<UUID> ids) {
        if (ids == null || ids.size() > 100) throw new IllegalArgumentException("At most 100 product IDs");
        var visible = catalog.visibleIds(ids);
        return visible.stream().collect(Collectors.toMap(id -> id, store::images));
    }

    public Rendition rendition(UUID id, String variant) {
        ProductImage image = store.image(id).orElseThrow(() -> new IllegalArgumentException("Image not found"));
        if (!catalog.visibleIds(List.of(image.productId())).contains(image.productId()))
            throw new IllegalArgumentException("Image not found");
        return readRendition(image, variant);
    }

    public Rendition adminRendition(UUID productId, UUID id, String variant) {
        if (!catalog.exists(productId)) throw new IllegalArgumentException("Product not found");
        ProductImage image = store.image(id).filter(value -> value.productId().equals(productId))
                .orElseThrow(() -> new IllegalArgumentException("Image not found"));
        return readRendition(image, variant);
    }

    private Rendition readRendition(ProductImage image, String variant) {
        String key = switch (variant) {
            case "card" -> image.cardKey();
            case "detail" -> image.detailKey();
            default -> throw new IllegalArgumentException("Invalid rendition");
        };
        return new Rendition(objects.get(key), "image/jpeg");
    }

    private void put(List<String> written, String key, byte[] bytes, String mime) {
        objects.put(key, bytes, mime);
        written.add(key);
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

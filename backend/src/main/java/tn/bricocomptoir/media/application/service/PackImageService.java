package tn.bricocomptoir.media.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import tn.bricocomptoir.media.application.port.out.PackPhotoLookup;
import tn.bricocomptoir.media.application.port.out.ImageProcessor;
import tn.bricocomptoir.media.application.port.out.PackImageStore;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import tn.bricocomptoir.media.domain.ImageRules;
import tn.bricocomptoir.media.domain.PackImage;

public final class PackImageService {
    public record Upload(byte[] bytes, String declaredType) { }
    public record Rendition(byte[] bytes, String type) { }
    private final PackPhotoLookup packs;
    private final ImageProcessor processor;
    private final PackImageStore store;
    private final ObjectStorage objects;

    public PackImageService(PackPhotoLookup packs, ImageProcessor processor, PackImageStore store, ObjectStorage objects) {
        this.packs = packs; this.processor = processor; this.store = store; this.objects = objects;
    }

    public List<PackImage> upload(UUID packId, List<Upload> uploads) {
        if (!packs.exists(packId)) throw new IllegalArgumentException("Pack not found");
        store.lockPack(packId);
        List<PackImage> prior = store.images(packId);
        ImageRules.capacity(prior.size(), uploads.size());
        // Validate the entire batch before writing the first object.
        var prepared = uploads.stream().map(upload -> processor.process(upload.bytes(), upload.declaredType())).toList();
        List<String> written = new ArrayList<>();
        try {
            for (int i = 0; i < uploads.size(); i++) {
                Upload upload = uploads.get(i);
                ImageProcessor.Processed image = prepared.get(i);
                UUID id = UUID.randomUUID();
                String prefix = "packs/" + packId + "/" + id + "/";
                String original = prefix + "original." + (image.sourceMime().equals("image/png") ? "png" : "jpg");
                String card = prefix + "card.jpg";
                String detail = prefix + "detail.jpg";
                put(written, original, upload.bytes(), image.sourceMime());
                put(written, card, image.card(), "image/jpeg");
                put(written, detail, image.detail(), "image/jpeg");
                store.insert(new PackImage(id, packId, original, card, detail, image.sourceMime(),
                        upload.bytes().length, digest(upload.bytes()), image.width(), image.height(),
                        prior.size() + i, prior.isEmpty() && i == 0, null));
            }
            return store.images(packId);
        } catch (RuntimeException failure) {
            written.forEach(key -> { try { objects.delete(key); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); } });
            throw failure;
        }
    }

    public List<PackImage> adminImages(UUID packId) {
        if (!packs.exists(packId)) throw new IllegalArgumentException("Pack not found");
        return store.images(packId);
    }

    public List<PackImage> reorder(UUID packId, List<UUID> ids, UUID primary) {
        if (!packs.exists(packId)) throw new IllegalArgumentException("Pack not found");
        store.lockPack(packId);
        ImageRules.orderIds(store.images(packId).stream().map(PackImage::id).toList(), ids, primary);
        store.reorder(packId, ids, primary);
        return store.images(packId);
    }

    public Map<UUID, List<PackImage>> publicImages(List<UUID> ids) {
        if (ids == null || ids.size() > 100) throw new IllegalArgumentException("At most 100 pack IDs");
        var visible = packs.visibleIds(ids);
        return visible.stream().collect(Collectors.toMap(id -> id, store::images));
    }

    public Rendition rendition(UUID id, String variant) {
        PackImage image = store.image(id).orElseThrow(() -> new IllegalArgumentException("Image not found"));
        if (!packs.visibleIds(List.of(image.packId())).contains(image.packId()))
            throw new IllegalArgumentException("Image not found");
        return readRendition(image, variant);
    }

    public Rendition adminRendition(UUID packId, UUID id, String variant) {
        if (!packs.exists(packId)) throw new IllegalArgumentException("Pack not found");
        PackImage image = store.image(id).filter(value -> value.packId().equals(packId))
                .orElseThrow(() -> new IllegalArgumentException("Image not found"));
        return readRendition(image, variant);
    }

    private Rendition readRendition(PackImage image, String variant) {
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

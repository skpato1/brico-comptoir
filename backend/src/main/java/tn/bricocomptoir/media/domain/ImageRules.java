package tn.bricocomptoir.media.domain;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class ImageRules {
    public static final int MAX_BYTES = 6 * 1024 * 1024;
    public static final int MAX_PER_PRODUCT = 12;
    private ImageRules() { }

    public static void source(long bytes, int width, int height, String declared, String detected) {
        if (bytes < 1 || bytes > MAX_BYTES) throw new IllegalArgumentException("Image size must be at most 6 MiB");
        if (!("image/jpeg".equals(detected) || "image/png".equals(detected)) || !detected.equals(declared))
            throw new IllegalArgumentException("Image must be a genuine JPEG or PNG matching its declared type");
        if (width < 320 || height < 320 || width > 6000 || height > 6000
                || (long) width * height > 24_000_000)
            throw new IllegalArgumentException("Image dimensions must be 320–6000 pixels and at most 24 MP");
    }

    public static void capacity(int current, int added) {
        if (added < 1 || added > 4 || current + added > MAX_PER_PRODUCT)
            throw new IllegalArgumentException("Upload 1–4 images, at most 12 per product");
    }

    public static void order(List<ProductImage> existing, List<UUID> ids, UUID primary) {
        if (ids == null || primary == null || ids.size() != existing.size()
                || !new HashSet<>(ids).equals(existing.stream().map(ProductImage::id).collect(java.util.stream.Collectors.toSet()))
                || !ids.contains(primary))
            throw new IllegalArgumentException("Order must contain each product image exactly once and select its primary image");
    }
}

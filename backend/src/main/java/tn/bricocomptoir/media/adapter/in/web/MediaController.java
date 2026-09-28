package tn.bricocomptoir.media.adapter.in.web;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.bricocomptoir.media.adapter.transaction.MediaTransactions;
import tn.bricocomptoir.media.application.service.MediaService;
import tn.bricocomptoir.media.domain.ImageRules;
import tn.bricocomptoir.media.domain.ProductImage;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
    private final MediaTransactions media;
    public MediaController(MediaTransactions media) { this.media = media; }

    @GetMapping("/media/products")
    public Map<UUID, List<ImageView>> publicImages(@RequestParam String ids) {
        List<UUID> productIds;
        try { productIds = Arrays.stream(ids.split(",", -1)).map(UUID::fromString).toList(); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid product IDs"); }
        return media.publicImages(productIds).entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().stream().map(ImageView::publicOf).toList()));
    }

    @GetMapping("/media/{id}/{variant:card|detail}")
    public ResponseEntity<byte[]> rendition(@PathVariable UUID id, @PathVariable String variant) {
        var output = media.rendition(id, variant);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).body(output.bytes());
    }

    @GetMapping("/admin/catalog/products/{productId}/images")
    public ResponseEntity<List<ImageView>> adminImages(@PathVariable UUID productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(media.adminImages(productId).stream().map(ImageView::adminOf).toList());
    }

    @GetMapping("/admin/catalog/products/{productId}/images/{id}/{variant:card|detail}")
    public ResponseEntity<byte[]> adminRendition(@PathVariable UUID productId, @PathVariable UUID id,
                                                  @PathVariable String variant) {
        var output = media.adminRendition(productId, id, variant);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).body(output.bytes());
    }

    @PostMapping(path = "/admin/catalog/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageView>> upload(@PathVariable UUID productId,
                                                    @RequestPart("files") List<MultipartFile> files) {
        if (files.size() < 1 || files.size() > 4) throw new IllegalArgumentException("Upload 1–4 images");
        for (MultipartFile file : files) {
            if (file.getSize() < 1 || file.getSize() > ImageRules.MAX_BYTES)
                throw new IllegalArgumentException("Image size must be at most 6 MiB");
        }
        var input = files.stream().map(file -> {
            try { return new MediaService.Upload(file.getBytes(), file.getContentType()); }
            catch (IOException failure) { throw new IllegalArgumentException("Cannot read image", failure); }
        }).toList();
        var images = media.upload(productId, input).stream().map(ImageView::adminOf).toList();
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/products/" + productId + "/images"))
                .cacheControl(CacheControl.noStore()).body(images);
    }

    @PutMapping("/admin/catalog/products/{productId}/images/order")
    public ResponseEntity<List<ImageView>> reorder(@PathVariable UUID productId, @RequestBody OrderInput input) {
        var images = media.reorder(productId, input.imageIds(), input.primaryImageId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(images.stream().map(ImageView::adminOf).toList());
    }

    public record OrderInput(List<UUID> imageIds, UUID primaryImageId) { }
    public record ImageView(UUID id, UUID productId, String cardUrl, String detailUrl,
                            int width, int height, int sortOrder, boolean primary) {
        static ImageView publicOf(ProductImage image) {
            String root = "/api/v1/media/" + image.id();
            return new ImageView(image.id(), image.productId(), root + "/card", root + "/detail",
                    image.width(), image.height(), image.sortOrder(), image.primary());
        }
        static ImageView adminOf(ProductImage image) {
            String root = "/api/v1/admin/catalog/products/" + image.productId() + "/images/" + image.id();
            return new ImageView(image.id(), image.productId(), root + "/card", root + "/detail",
                    image.width(), image.height(), image.sortOrder(), image.primary());
        }
    }
}

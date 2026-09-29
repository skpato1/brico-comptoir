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
import tn.bricocomptoir.media.adapter.transaction.PackImageTransactions;
import tn.bricocomptoir.media.application.service.PackImageService;
import tn.bricocomptoir.media.domain.ImageRules;
import tn.bricocomptoir.media.domain.PackImage;

@RestController
@RequestMapping("/api/v1")
public class PackImageController {
    private final PackImageTransactions media;
    public PackImageController(PackImageTransactions media) { this.media = media; }

    @GetMapping("/media/packs")
    public Map<UUID, List<ImageView>> publicImages(@RequestParam String ids) {
        List<UUID> packIds;
        try { packIds = Arrays.stream(ids.split(",", -1)).map(UUID::fromString).toList(); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid pack IDs"); }
        return media.publicImages(packIds).entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().stream().map(ImageView::publicOf).toList()));
    }

    @GetMapping("/media/packs/{id}/{variant:card|detail}")
    public ResponseEntity<byte[]> rendition(@PathVariable UUID id, @PathVariable String variant) {
        var output = media.rendition(id, variant);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).body(output.bytes());
    }

    @GetMapping("/admin/packs/{packId}/images")
    public ResponseEntity<List<ImageView>> adminImages(@PathVariable UUID packId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(media.adminImages(packId).stream().map(ImageView::adminOf).toList());
    }

    @GetMapping("/admin/packs/{packId}/images/{id}/{variant:card|detail}")
    public ResponseEntity<byte[]> adminRendition(@PathVariable UUID packId, @PathVariable UUID id,
                                                  @PathVariable String variant) {
        var output = media.adminRendition(packId, id, variant);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).body(output.bytes());
    }

    @PostMapping(path = "/admin/packs/{packId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageView>> upload(@PathVariable UUID packId,
                                                    @RequestPart("files") List<MultipartFile> files) {
        if (files.size() < 1 || files.size() > 4) throw new IllegalArgumentException("Upload 1–4 images");
        for (MultipartFile file : files) {
            if (file.getSize() < 1 || file.getSize() > ImageRules.MAX_BYTES)
                throw new IllegalArgumentException("Image size must be at most 6 MiB");
        }
        var input = files.stream().map(file -> {
            try { return new PackImageService.Upload(file.getBytes(), file.getContentType()); }
            catch (IOException failure) { throw new IllegalArgumentException("Cannot read image", failure); }
        }).toList();
        var images = media.upload(packId, input).stream().map(ImageView::adminOf).toList();
        return ResponseEntity.created(URI.create("/api/v1/admin/packs/" + packId + "/images"))
                .cacheControl(CacheControl.noStore()).body(images);
    }

    @PutMapping("/admin/packs/{packId}/images/order")
    public ResponseEntity<List<ImageView>> reorder(@PathVariable UUID packId, @RequestBody OrderInput input) {
        var images = media.reorder(packId, input.imageIds(), input.primaryImageId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(images.stream().map(ImageView::adminOf).toList());
    }

    public record OrderInput(List<UUID> imageIds, UUID primaryImageId) { }
    public record ImageView(UUID id, UUID packId, String cardUrl, String detailUrl,
                            int width, int height, int sortOrder, boolean primary) {
        static ImageView publicOf(PackImage image) {
            String root = "/api/v1/media/packs/" + image.id();
            return new ImageView(image.id(), image.packId(), root + "/card", root + "/detail",
                    image.width(), image.height(), image.sortOrder(), image.primary());
        }
        static ImageView adminOf(PackImage image) {
            String root = "/api/v1/admin/packs/" + image.packId() + "/images/" + image.id();
            return new ImageView(image.id(), image.packId(), root + "/card", root + "/detail",
                    image.width(), image.height(), image.sortOrder(), image.primary());
        }
    }
}

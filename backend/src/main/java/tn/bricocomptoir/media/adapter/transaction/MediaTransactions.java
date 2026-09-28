package tn.bricocomptoir.media.adapter.transaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.media.application.service.MediaService;
import tn.bricocomptoir.media.domain.ProductImage;

@Service
public class MediaTransactions {
    private final MediaService service;
    public MediaTransactions(MediaService service) { this.service = service; }
    @Transactional public List<ProductImage> upload(UUID product, List<MediaService.Upload> files) {
        return service.upload(product, files);
    }
    @Transactional public List<ProductImage> reorder(UUID product, List<UUID> ids, UUID primary) {
        return service.reorder(product, ids, primary);
    }
    @Transactional(readOnly = true) public List<ProductImage> adminImages(UUID product) { return service.adminImages(product); }
    @Transactional(readOnly = true) public Map<UUID, List<ProductImage>> publicImages(List<UUID> ids) {
        return service.publicImages(ids);
    }
    @Transactional(readOnly = true) public MediaService.Rendition rendition(UUID id, String variant) {
        return service.rendition(id, variant);
    }
    @Transactional(readOnly = true) public MediaService.Rendition adminRendition(UUID product, UUID id, String variant) {
        return service.adminRendition(product, id, variant);
    }
}

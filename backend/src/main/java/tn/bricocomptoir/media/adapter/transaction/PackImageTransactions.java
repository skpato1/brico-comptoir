package tn.bricocomptoir.media.adapter.transaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.media.application.service.PackImageService;
import tn.bricocomptoir.media.domain.PackImage;

@Service
public class PackImageTransactions {
    private final PackImageService service;
    public PackImageTransactions(PackImageService service) { this.service = service; }
    @Transactional public List<PackImage> upload(UUID product, List<PackImageService.Upload> files) {
        return service.upload(product, files);
    }
    @Transactional public List<PackImage> reorder(UUID product, List<UUID> ids, UUID primary) {
        return service.reorder(product, ids, primary);
    }
    @Transactional(readOnly = true) public List<PackImage> adminImages(UUID product) { return service.adminImages(product); }
    @Transactional(readOnly = true) public Map<UUID, List<PackImage>> publicImages(List<UUID> ids) {
        return service.publicImages(ids);
    }
    @Transactional(readOnly = true) public PackImageService.Rendition rendition(UUID id, String variant) {
        return service.rendition(id, variant);
    }
    @Transactional(readOnly = true) public PackImageService.Rendition adminRendition(UUID product, UUID id, String variant) {
        return service.adminRendition(product, id, variant);
    }
}

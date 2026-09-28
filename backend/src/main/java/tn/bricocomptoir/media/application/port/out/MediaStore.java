package tn.bricocomptoir.media.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.media.domain.ProductImage;

public interface MediaStore {
    void lockProduct(UUID productId);
    List<ProductImage> images(UUID productId);
    Optional<ProductImage> image(UUID id);
    void insert(ProductImage image);
    void reorder(UUID productId, List<UUID> ids, UUID primary);
}

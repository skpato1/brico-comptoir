package tn.bricocomptoir.media.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.media.domain.PackImage;

public interface PackImageStore {
    void lockPack(UUID packId);
    List<PackImage> images(UUID packId);
    Optional<PackImage> image(UUID id);
    void insert(PackImage image);
    void reorder(UUID packId, List<UUID> ids, UUID primary);
}

package tn.bricocomptoir.media.application.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PackPhotoLookup {
    boolean exists(UUID packId);
    Set<UUID> visibleIds(List<UUID> packIds);
}

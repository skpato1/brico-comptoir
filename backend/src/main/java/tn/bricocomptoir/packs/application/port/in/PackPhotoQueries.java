package tn.bricocomptoir.packs.application.port.in;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Visibility for media; publication includes the visibility of required components. */
public interface PackPhotoQueries {
    boolean exists(UUID packId);
    Set<UUID> visibleIds(List<UUID> packIds);
}

package tn.bricocomptoir.media.application.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CatalogProductLookup {
    boolean exists(UUID id);
    Set<UUID> visibleIds(List<UUID> ids);
}

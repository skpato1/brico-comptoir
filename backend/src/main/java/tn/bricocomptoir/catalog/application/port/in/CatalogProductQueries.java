package tn.bricocomptoir.catalog.application.port.in;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CatalogProductQueries {
    boolean exists(UUID id);
    Set<UUID> visibleIds(List<UUID> ids);
}

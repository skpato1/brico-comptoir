package tn.bricocomptoir.content.application.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BestSellerProductLookup {
    Set<UUID> visibleIds(List<UUID> ids);
}

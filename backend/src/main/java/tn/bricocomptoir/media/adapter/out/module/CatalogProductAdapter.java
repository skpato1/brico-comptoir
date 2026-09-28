package tn.bricocomptoir.media.adapter.out.module;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.catalog.application.port.in.CatalogProductQueries;
import tn.bricocomptoir.media.application.port.out.CatalogProductLookup;

@Component
public class CatalogProductAdapter implements CatalogProductLookup {
    private final CatalogProductQueries catalog;
    public CatalogProductAdapter(CatalogProductQueries catalog) { this.catalog = catalog; }
    @Override public boolean exists(UUID id) { return catalog.exists(id); }
    @Override public Set<UUID> visibleIds(List<UUID> ids) { return catalog.visibleIds(ids); }
}

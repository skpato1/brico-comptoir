package tn.bricocomptoir.content.adapter.out.module;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.catalog.application.port.in.CatalogProductQueries;
import tn.bricocomptoir.content.application.port.out.BestSellerProductLookup;

@Component
public class CatalogBestSellerAdapter implements BestSellerProductLookup {
    private final CatalogProductQueries catalog;
    public CatalogBestSellerAdapter(CatalogProductQueries catalog) { this.catalog = catalog; }
    @Override public Set<UUID> visibleIds(List<UUID> ids) { return catalog.visibleIds(ids); }
}

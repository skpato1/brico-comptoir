package tn.bricocomptoir.media.adapter.out.module;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.media.application.port.out.PackPhotoLookup;
import tn.bricocomptoir.packs.application.port.in.PackPhotoQueries;

@Component
public class PackPhotoAdapter implements PackPhotoLookup {
    private final PackPhotoQueries packs;
    public PackPhotoAdapter(PackPhotoQueries packs) { this.packs = packs; }
    @Override public boolean exists(UUID id) { return packs.exists(id); }
    @Override public Set<UUID> visibleIds(List<UUID> ids) { return packs.visibleIds(ids); }
}

package tn.bricocomptoir.packs.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tn.bricocomptoir.packs.domain.PackModels.*;

public interface PackStore {
    List<Pack> packs();
    Page<Pack> page(String query,int page,int size);
    Optional<Pack> pack(UUID id);
    Optional<PackVariant> variant(UUID id);
    Pack savePack(Pack pack, boolean create);
    PackVariant saveVariant(PackVariant variant, boolean create);
}

package tn.bricocomptoir.packs.adapter.transaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.packs.application.port.in.PackOfferQueries;
import tn.bricocomptoir.packs.application.service.PackService;
import tn.bricocomptoir.packs.domain.PackModels.*;

@Primary
@Service
public class PackTransactions implements PackOfferQueries {
    private final PackService service;
    public PackTransactions(PackService service) { this.service = service; }

    @Transactional(readOnly = true) public List<Pack> packs(boolean admin) { return service.packs(admin); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Pack> page(String q,int page,int size) { return service.page(q,page,size); }
    @Transactional(readOnly = true) public Pack pack(UUID id, boolean admin) { return service.pack(id, admin); }
    @Transactional(readOnly = true) public Map<UUID, String> componentNames(Pack pack) { return service.componentNames(pack); }
    @Transactional(readOnly = true) public Map<UUID, Long> availability(Pack pack) {
        return service.availability(pack);
    }
    @Transactional(readOnly = true) public long availability(PackVariant variant) {
        return service.availability(variant);
    }
    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Offer> offer(UUID variantId) {
        return service.offer(variantId);
    }
    @Transactional public Pack savePack(UUID id, String code, String name, String slogan,
                                        String guide, String status, Long version) {
        return service.savePack(id, code, name, slogan, guide, status, version);
    }
    @Transactional public PackVariant saveVariant(UUID id, UUID packId, String code, String label,
                                                   String priceTnd, String status,
                                                   List<Component> components, Long version) {
        return service.saveVariant(id, packId, code, label, priceTnd, status, components, version);
    }
}

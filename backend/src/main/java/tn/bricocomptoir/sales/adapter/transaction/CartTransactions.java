package tn.bricocomptoir.sales.adapter.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.sales.application.service.CartService;
import tn.bricocomptoir.sales.domain.CartModels.*;

@Service
public class CartTransactions {
    private final CartService service;
    private final tn.bricocomptoir.sales.application.port.out.CustomerContact contact;
    public CartTransactions(CartService service,tn.bricocomptoir.sales.application.port.out.CustomerContact contact) { this.service = service;this.contact=contact; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Quote estimate(List<Line> lines) { return service.estimate(lines); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public View view(UUID customerId) { return service.view(customerId); }
    @Transactional public View replace(UUID customerId, long version, List<Line> lines) {
        contact.lockPurchase(customerId);
        return service.replace(customerId, version, lines);
    }
    @Transactional public View merge(UUID customerId, UUID mergeId, List<Line> lines) {
        contact.lockPurchase(customerId);
        return service.merge(customerId, mergeId, lines);
    }
}

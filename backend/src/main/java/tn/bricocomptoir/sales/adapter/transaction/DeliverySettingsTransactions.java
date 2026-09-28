package tn.bricocomptoir.sales.adapter.transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.sales.domain.DeliverySettings;
import tn.bricocomptoir.sales.application.service.DeliverySettingsService;
import tn.bricocomptoir.sales.application.port.out.DeliverySettingsStore;
@Service
public class DeliverySettingsTransactions {
    private final DeliverySettingsService service;
    public DeliverySettingsTransactions(DeliverySettingsStore store) { service=new DeliverySettingsService(store); }
    @Transactional(readOnly=true) public DeliverySettings get() { return service.get(); }
    @Transactional public DeliverySettings save(DeliverySettings value,String actor) { return service.save(value,actor); }
}

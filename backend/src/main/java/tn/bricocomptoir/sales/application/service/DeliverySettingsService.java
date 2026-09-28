package tn.bricocomptoir.sales.application.service;
import tn.bricocomptoir.sales.domain.DeliverySettings;
import tn.bricocomptoir.sales.application.port.out.DeliverySettingsStore;
public final class DeliverySettingsService {
    private final DeliverySettingsStore store;
    public DeliverySettingsService(DeliverySettingsStore store) { this.store=store; }
    public DeliverySettings get() { return store.get(); }
    public DeliverySettings save(DeliverySettings value,String actor) { return store.save(value.checked(),actor); }
}

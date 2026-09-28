package tn.bricocomptoir.sales.application.port.out;
import tn.bricocomptoir.sales.domain.DeliverySettings;
public interface DeliverySettingsStore {
    DeliverySettings get();
    DeliverySettings save(DeliverySettings value,String actor);
}

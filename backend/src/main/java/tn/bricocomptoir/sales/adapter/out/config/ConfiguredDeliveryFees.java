package tn.bricocomptoir.sales.adapter.out.config;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.sales.application.port.out.DeliveryFees;
import tn.bricocomptoir.sales.domain.CheckoutFailure;
import tn.bricocomptoir.sales.domain.OrderModels.Address;
import tn.bricocomptoir.sales.domain.OrderRules;

@Component
public class ConfiguredDeliveryFees implements DeliveryFees {
    private final tn.bricocomptoir.sales.application.port.out.DeliverySettingsStore settings;
    public ConfiguredDeliveryFees(tn.bricocomptoir.sales.application.port.out.DeliverySettingsStore settings) {
        this.settings = settings;
    }
    @Override public BigDecimal forAddress(Address address) {
        var current = settings.get();
        if (!current.enabled()) throw new CheckoutFailure("CHECKOUT_UNAVAILABLE");
        var served = current.governorates();
        BigDecimal fee = OrderRules.fee(current.amountTnd());
        if (served.isEmpty() || !OrderRules.GOVERNORATES.containsAll(served)) throw new CheckoutFailure("CHECKOUT_UNAVAILABLE");
        if (!served.contains(address.governorate())) throw new CheckoutFailure("DELIVERY_ZONE_UNAVAILABLE");
        return fee;
    }
}

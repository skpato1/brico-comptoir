package tn.bricocomptoir.sales.domain;
import java.util.Set;
public record DeliverySettings(boolean enabled, String amountTnd, Set<String> governorates, long version) {
    public DeliverySettings { governorates = governorates == null ? Set.of() : Set.copyOf(governorates); }
    public DeliverySettings checked() {
        if (version < 0 || !OrderRules.GOVERNORATES.containsAll(governorates) || enabled && governorates.isEmpty())
            throw new IllegalArgumentException("Select valid delivery zones");
        try {
            return new DeliverySettings(enabled, OrderRules.fee(amountTnd).toPlainString(), governorates, version);
        } catch (CheckoutFailure invalid) { throw new IllegalArgumentException("Invalid delivery fee"); }
    }
}

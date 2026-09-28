package tn.bricocomptoir.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tn.bricocomptoir.sales.domain.CartModels.Kind;

public final class OrderModels {
    private OrderModels() { }
    public enum Status { CONFIRMED, PREPARING, SHIPPED, DELIVERED, CANCELLED }
    public record Actor(String scope, UUID customerId, boolean manager) { }
    public record Address(String recipient, String phone, String street, String city,
                          String postalCode, String governorate, String country, String email) {
        public Address(String recipient,String phone,String street,String city,String postalCode,String governorate,String country) {
            this(recipient,phone,street,city,postalCode,governorate,country,null);
        }
    }
    public record Item(Kind kind, UUID offerId, long quantity, long offerVersion, long parentVersion) { }
    public record SkuSnapshot(UUID skuId, String sku, String label, long quantity,
                              long version, long productVersion) { }
    public record OrderLine(Kind kind, UUID offerId, String label, long quantity,
                            BigDecimal unitPriceTnd, BigDecimal lineTotalTnd,
                            long offerVersion, long parentVersion, List<SkuSnapshot> components) {
        public OrderLine { components = List.copyOf(components); }
    }
    public record Snapshot(Address address, List<OrderLine> items, BigDecimal subtotalTnd,
                           BigDecimal deliveryTnd, BigDecimal totalTnd, String quoteHash) {
        public Snapshot { items = List.copyOf(items); }
    }
    public record Order(UUID id, String ownerScope, UUID customerId, Status status,
                        Instant createdAt, Snapshot snapshot) { }
    public record Receipt(String fingerprint, UUID orderId) { }
}

package tn.bricocomptoir.sales.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tn.bricocomptoir.sales.application.port.out.*;
import tn.bricocomptoir.sales.domain.CartModels.*;
import tn.bricocomptoir.sales.domain.*;
import tn.bricocomptoir.sales.domain.OrderModels.*;

public final class OrderService {
    private final OrderStore store;
    private final CheckoutOffers offers;
    private final StockLookup availability;
    private final OrderStock stock;
    private final DeliveryFees delivery;
    private final Clock clock;
    private final OrderNotifications notifications;
    private final CustomerContact contact;
    public OrderService(OrderStore store, CheckoutOffers offers, StockLookup availability,
                        OrderStock stock, DeliveryFees delivery, Clock clock,OrderNotifications notifications,CustomerContact contact) {
        this.store = store; this.offers = offers; this.availability = availability;
        this.stock = stock; this.delivery = delivery; this.clock = clock;
        this.notifications=notifications;this.contact=contact;
    }

    public Snapshot preview(Actor actor, List<Item> requested, Address address) {
        requireBuyer(actor);
        Snapshot snapshot = snapshot(actor.scope(), OrderRules.items(requested), address(actor,address));
        if (demand(snapshot).entrySet().stream().anyMatch(e -> availability.available(e.getKey()) < e.getValue()))
            throw new CheckoutFailure("STOCK_UNAVAILABLE");
        return snapshot;
    }

    public Order place(Actor actor, UUID key, List<Item> requested, Address address, String quoteHash) {
        requireBuyer(actor);
        if(actor.customerId()!=null)contact.lockPurchase(actor.customerId());
        else store.lockGuestPurchase(actor.scope());
        List<Item> items = OrderRules.items(requested);
        Address valid = address(actor,address);
        if (key == null || quoteHash == null || !quoteHash.matches("[0-9a-f]{64}"))
            throw new CheckoutFailure("INVALID_CHECKOUT");
        List<String> tokens = new ArrayList<>(OrderRules.addressTokens(valid));
        tokens.add(quoteHash);
        for (Item i : items) tokens.addAll(List.of(i.kind().name(), i.offerId().toString(),
                Long.toString(i.quantity()), Long.toString(i.offerVersion()), Long.toString(i.parentVersion())));
        String fingerprint = OrderRules.hash(tokens);
        Receipt receipt = store.acquire(actor.scope(), key, fingerprint);
        if (!receipt.fingerprint().equals(fingerprint)) throw new CheckoutFailure("IDEMPOTENCY_CONFLICT");
        if (receipt.orderId() != null) return get(actor, receipt.orderId(), false);
        Snapshot snapshot = snapshot(actor.scope(), items, valid);
        if (!snapshot.quoteHash().equals(quoteHash)) throw new CheckoutFailure("OFFER_CHANGED");
        UUID id = UUID.randomUUID();
        stock.reserve(id, demand(snapshot));
        Order order = new Order(id, actor.scope(), actor.customerId(), Status.CONFIRMED, clock.instant(), snapshot);
        store.insert(order);
        store.complete(actor.scope(), key, id);
        notifications.changed(order,true);
        return order;
    }

    public Order get(Actor actor, UUID id, boolean lock) {
        Order order = store.find(id, lock).orElseThrow(() -> new CheckoutFailure("ORDER_NOT_FOUND"));
        if (!actor.manager() && !order.ownerScope().equals(actor.scope())) throw new CheckoutFailure("ORDER_NOT_FOUND");
        return order;
    }

    public List<Order> list(Actor actor, Status status, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new CheckoutFailure("INVALID_CHECKOUT");
        if (!actor.manager() && actor.customerId() == null) throw new CheckoutFailure("ORDER_NOT_FOUND");
        return store.list(actor.manager() ? null : actor.scope(), status, page * size, size);
    }

    public Order transition(Actor actor, UUID id, Status target) {
        Order prior = get(actor, id, true);
        OrderRules.transition(prior.status(), target, actor.manager());
        if (prior.status() == target) return prior;
        if (target == Status.CANCELLED) stock.release(id);
        if (target == Status.SHIPPED) stock.consume(id);
        store.transition(id, target, actor.scope());
        Order changed=new Order(prior.id(), prior.ownerScope(), prior.customerId(), target, prior.createdAt(), prior.snapshot());
        notifications.changed(changed,false);
        return changed;
    }

    private Snapshot snapshot(String ownerScope, List<Item> items, Address address) {
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal subtotal = new BigDecimal("0.000");
        List<String> tokens = new ArrayList<>(OrderRules.addressTokens(address));
        tokens.add(ownerScope);
        for (Item i : items) {
            var offer = offers.published(i.kind(), i.offerId()).orElseThrow(() -> new CheckoutFailure("OFFER_CHANGED"));
            if (offer.version() != i.offerVersion() || offer.parentVersion() != i.parentVersion())
                throw new CheckoutFailure("OFFER_CHANGED");
            BigDecimal price = offer.priceTnd().multiply(BigDecimal.valueOf(i.quantity()));
            subtotal = subtotal.add(price);
            lines.add(new OrderLine(i.kind(), i.offerId(), offer.label(), i.quantity(), offer.priceTnd(), price,
                    offer.version(), offer.parentVersion(), offer.components()));
            tokens.addAll(List.of(i.kind().name(), i.offerId().toString(), Long.toString(i.quantity()), offer.label(),
                    offer.priceTnd().toPlainString(), Long.toString(offer.version()), Long.toString(offer.parentVersion())));
            for (SkuSnapshot c : offer.components()) tokens.addAll(List.of(c.skuId().toString(), c.sku(), c.label(),
                    Long.toString(c.quantity()), Long.toString(c.version()), Long.toString(c.productVersion())));
        }
        BigDecimal fee = delivery.forAddress(address);
        tokens.add(fee.toPlainString());
        Snapshot result = new Snapshot(address, lines, subtotal, fee, subtotal.add(fee), OrderRules.hash(tokens));
        demand(result); // Bound SKU cardinality/overflow before touching inventory.
        return result;
    }

    private Map<UUID, Long> demand(Snapshot snapshot) {
        var result = CartRules.demand(snapshot.items().stream().map(line -> new DemandLine(line.quantity(),
                line.components().stream().map(c -> new SkuRequirement(c.skuId(), c.quantity())).toList())).toList());
        if (result.isEmpty() || result.size() > 100) throw new CheckoutFailure("INVALID_CHECKOUT");
        return result;
    }
    private void requireBuyer(Actor actor) {
        if (actor == null || actor.manager() || actor.scope() == null) throw new CheckoutFailure("INVALID_CHECKOUT");
    }
    private Address address(Actor actor,Address input) {
        if(input==null)throw new CheckoutFailure("INVALID_CHECKOUT");
        String email=actor.customerId()==null?input.email():contact.email(actor.customerId()).orElse(null);
        return OrderRules.address(new Address(input.recipient(),input.phone(),input.street(),input.city(),input.postalCode(),input.governorate(),input.country(),email));
    }
}

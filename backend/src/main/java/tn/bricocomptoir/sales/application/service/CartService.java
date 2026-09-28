package tn.bricocomptoir.sales.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tn.bricocomptoir.sales.application.port.out.CartStore;
import tn.bricocomptoir.sales.application.port.out.OfferLookup;
import tn.bricocomptoir.sales.application.port.out.StockLookup;
import tn.bricocomptoir.sales.domain.CartModels.*;
import tn.bricocomptoir.sales.domain.CartRules;

public final class CartService {
    private final CartStore store;
    private final OfferLookup offers;
    private final StockLookup stock;

    public CartService(CartStore store, OfferLookup offers, StockLookup stock) {
        this.store = store; this.offers = offers; this.stock = stock;
    }

    public Quote estimate(List<Line> lines) { return quote(CartRules.normalize(lines)); }

    public View view(UUID customerId) {
        requireCustomer(customerId);
        Cart cart = store.read(customerId).orElse(new Cart(customerId, 0, List.of()));
        return new View(cart, quote(cart.lines()));
    }

    public View replace(UUID customerId, long expectedVersion, List<Line> lines) {
        requireCustomer(customerId);
        if (expectedVersion < 0) throw new IllegalArgumentException("Cart version required");
        List<Line> normalized = CartRules.normalize(lines);
        Cart current = store.lock(customerId);
        if (current.version() != expectedVersion) throw new IllegalStateException("Cart version conflict");
        Cart updated = current.lines().equals(normalized) ? current
                : store.save(new Cart(customerId, current.version(), normalized));
        return new View(updated, quote(updated.lines()));
    }

    public View merge(UUID customerId, UUID mergeId, List<Line> lines) {
        requireCustomer(customerId);
        if (mergeId == null) throw new IllegalArgumentException("Merge ID required");
        List<Line> normalized = CartRules.normalize(lines);
        String fingerprint = CartRules.fingerprint(normalized);
        Cart current = store.lock(customerId);
        var prior = store.mergeFingerprint(customerId, mergeId);
        if (prior.isPresent()) {
            if (!prior.get().equals(fingerprint)) throw new IllegalStateException("Merge ID conflict");
            return new View(current, quote(current.lines()));
        }
        List<Line> combined = CartRules.merge(current.lines(), normalized);
        Cart updated = current.lines().equals(combined) ? current
                : store.save(new Cart(customerId, current.version(), combined));
        store.recordMerge(customerId, mergeId, fingerprint);
        return new View(updated, quote(updated.lines()));
    }

    private Quote quote(List<Line> lines) {
        List<DisplayLine> display = new ArrayList<>();
        List<DemandLine> demandLines = new ArrayList<>();
        BigDecimal subtotal = new BigDecimal("0.000");
        boolean unresolved = false;
        for (Line line : lines) {
            var offer = offers.published(line.kind(), line.offerId());
            if (offer.isEmpty()) {
                unresolved = true;
                display.add(new DisplayLine(line, null, null, null, null, null));
                continue;
            }
            OfferLookup.Offer found = offer.get();
            BigDecimal linePrice = found.priceTnd().multiply(BigDecimal.valueOf(line.quantity()));
            subtotal = subtotal.add(linePrice);
            display.add(new DisplayLine(line, found.label(), found.priceTnd(), linePrice,
                    found.version(), found.parentVersion()));
            demandLines.add(new DemandLine(line.quantity(), found.components()));
        }
        Map<UUID, Long> requirements = CartRules.demand(demandLines);
        List<Shortage> shortages = requirements.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> new Shortage(entry.getKey(), entry.getValue(), stock.available(entry.getKey())))
                .filter(shortage -> shortage.available() < shortage.required()).toList();
        return new Quote(List.copyOf(display), unresolved ? null : subtotal, shortages);
    }

    private void requireCustomer(UUID customerId) {
        if (customerId == null) throw new IllegalArgumentException("Customer required");
    }
}

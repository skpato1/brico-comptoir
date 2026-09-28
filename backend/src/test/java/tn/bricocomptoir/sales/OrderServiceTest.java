package tn.bricocomptoir.sales;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.sales.application.port.out.*;
import tn.bricocomptoir.sales.application.service.OrderService;
import tn.bricocomptoir.sales.domain.*;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    @Test void sharedSkuIsAggregatedAndConfirmedAmountsAreExact() {
        UUID sku = UUID.randomUUID(), pack = UUID.randomUUID();
        OrderStore store = mock(OrderStore.class);
        OrderStock stock = mock(OrderStock.class);
        CheckoutOffers offers = (kind, id) -> Optional.of(new CheckoutOffers.Offer("Test", new BigDecimal("2.375"), 0, 0,
                List.of(new SkuSnapshot(sku, "TEST", "Test", kind == Kind.PACK ? 2 : 1, 0, 0))));
        OrderService service = new OrderService(store, offers, id -> 100, stock, a -> new BigDecimal("7.000"), Clock.systemUTC(), mock(OrderNotifications.class), mock(CustomerContact.class));
        List<Item> items = List.of(new Item(Kind.PRODUCT, sku, 1, 0, 0), new Item(Kind.PACK, pack, 2, 0, 0));
        Address address = new Address("Test", "20123456", "Rue", "Tunis", "1000", "TUNIS", "TN");
        Actor actor = new Actor("G:" + UUID.randomUUID(), null, false);
        Snapshot preview = service.preview(actor, items, address);
        assertThat(preview.totalTnd()).isEqualByComparingTo("14.125");
        when(store.acquire(anyString(), any(), anyString())).thenAnswer(invocation -> new Receipt(invocation.getArgument(2), null));
        assertThatThrownBy(() -> service.place(new Actor("G:" + UUID.randomUUID(), null, false),
                UUID.randomUUID(), items, address, preview.quoteHash())).hasMessage("OFFER_CHANGED");
        Order order = service.place(actor, UUID.randomUUID(), items, address, preview.quoteHash());
        verify(stock).reserve(order.id(), Map.of(sku, 5L));
        assertThat(order.snapshot().items()).hasSize(2);
        assertThatThrownBy(() -> order.snapshot().items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void changedPriceCompositionOrShippingRequiresAnewConfirmation() {
        UUID sku = UUID.randomUUID();
        OrderStore store = mock(OrderStore.class);
        OrderStock stock = mock(OrderStock.class);
        CheckoutOffers offers = mock(CheckoutOffers.class);
        DeliveryFees fees = mock(DeliveryFees.class);
        var original = new CheckoutOffers.Offer("Test", new BigDecimal("2.375"), 0, 0,
                List.of(new SkuSnapshot(sku, "TEST", "Test", 1, 0, 0)));
        when(offers.published(Kind.PRODUCT, sku)).thenReturn(Optional.of(original));
        when(fees.forAddress(any())).thenReturn(new BigDecimal("7.000"));
        when(store.acquire(anyString(), any(), anyString())).thenAnswer(i -> new Receipt(i.getArgument(2), null));
        OrderService service = new OrderService(store, offers, id -> 100, stock, fees, Clock.systemUTC(), mock(OrderNotifications.class), mock(CustomerContact.class));
        var items = List.of(new Item(Kind.PRODUCT, sku, 1, 0, 0));
        var address = new Address("Test", "20123456", "Rue", "Tunis", "1000", "TUNIS", "TN");
        Actor actor = new Actor("G:" + UUID.randomUUID(), null, false);
        String hash = service.preview(actor, items, address).quoteHash();
        when(fees.forAddress(any())).thenReturn(new BigDecimal("8.000"));
        assertThatThrownBy(() -> service.place(actor, UUID.randomUUID(), items, address, hash))
                .isInstanceOf(CheckoutFailure.class).hasMessage("OFFER_CHANGED");
        when(fees.forAddress(any())).thenReturn(new BigDecimal("7.000"));
        when(offers.published(Kind.PRODUCT, sku)).thenReturn(Optional.of(new CheckoutOffers.Offer("Test", new BigDecimal("3.000"), 1, 0,
                original.components())));
        assertThatThrownBy(() -> service.preview(actor, items, address)).hasMessage("OFFER_CHANGED");
        verifyNoInteractions(stock);
    }
}

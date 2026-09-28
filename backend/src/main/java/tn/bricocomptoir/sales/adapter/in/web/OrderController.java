package tn.bricocomptoir.sales.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.bricocomptoir.sales.adapter.transaction.OrderTransactions;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderTransactions orders;
    private static final String PRINCIPAL_ID = "#this instanceof T(java.lang.String) ? null : #this.id";
    public OrderController(OrderTransactions orders) { this.orders = orders; }

    @PostMapping("/checkout/preview")
    public ResponseEntity<SummaryView> preview(@Valid @RequestBody PreviewInput input,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID customerId,
            Authentication authentication, HttpServletRequest request) {
        return ok(summary(orders.preview(buyer(customerId, authentication, request), items(input.items()), input.address())));
    }
    @PostMapping("/orders")
    public ResponseEntity<OrderView> place(@Valid @RequestBody PlaceInput input,
            @RequestHeader("Idempotency-Key") UUID key,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID customerId,
            Authentication authentication, HttpServletRequest request) {
        Order result = orders.place(buyer(customerId, authentication, request), key,
                items(input.items()), input.address(), input.quoteHash());
        return ResponseEntity.created(java.net.URI.create("/api/v1/orders/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(view(result));
    }
    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderView> get(@PathVariable UUID id,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID customerId,
            Authentication authentication, HttpServletRequest request) {
        return ok(view(orders.get(buyer(customerId, authentication, request), id)));
    }
    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<OrderView> cancel(@PathVariable UUID id,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID customerId,
            Authentication authentication, HttpServletRequest request) {
        return ok(view(orders.transition(buyer(customerId, authentication, request), id, Status.CANCELLED)));
    }
    @GetMapping("/orders")
    public ResponseEntity<List<OrderView>> history(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID customerId,
            Authentication authentication, HttpServletRequest request) {
        return ok(orders.list(buyer(customerId, authentication, request), null, page, size).stream().map(this::view).toList());
    }
    @GetMapping("/admin/orders")
    public ResponseEntity<List<OrderView>> adminList(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Status status,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID id) {
        return ok(orders.list(manager(id), status, page, size).stream().map(o -> {
            var s=o.snapshot();
            return view(new Order(o.id(),o.ownerScope(),o.customerId(),o.status(),o.createdAt(),
                new Snapshot(new Address("Ouvrir pour consulter","","","","","","TN",null),s.items(),s.subtotalTnd(),s.deliveryTnd(),s.totalTnd(),s.quoteHash())));
        }).toList());
    }
    @GetMapping("/admin/orders/{id}")
    public ResponseEntity<OrderView> adminGet(@PathVariable UUID id,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID actorId) {
        return ok(view(orders.get(manager(actorId), id)));
    }
    @PostMapping("/admin/orders/{id}/{action:prepare|ship|deliver|cancel}")
    public ResponseEntity<OrderView> adminTransition(@PathVariable UUID id, @PathVariable String action,
            @AuthenticationPrincipal(expression = PRINCIPAL_ID) UUID actorId) {
        Status target = switch (action) {
            case "prepare" -> Status.PREPARING; case "ship" -> Status.SHIPPED;
            case "deliver" -> Status.DELIVERED; default -> Status.CANCELLED;
        };
        return ok(view(orders.transition(manager(actorId), id, target)));
    }
    private Actor manager(UUID id) { return new Actor("C:" + id, id, true); }
    private Actor buyer(UUID id, Authentication authentication, HttpServletRequest request) {
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (id == null || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")))
                throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            return new Actor("C:" + id, id, false);
        }
        var session = request.getSession(true);
        String scope;
        synchronized (session) {
            scope = (String) session.getAttribute("sales.guest-scope");
            if (scope == null) { scope = "G:" + UUID.randomUUID(); session.setAttribute("sales.guest-scope", scope); }
        }
        return new Actor(scope, null, false);
    }
    private List<Item> items(List<ItemInput> items) {
        return items.stream().map(i -> new Item(i.kind(), i.offerId(), i.quantity(), i.offerVersion(), i.parentVersion())).toList();
    }
    private <T> ResponseEntity<T> ok(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    private MoneyView money(java.math.BigDecimal amount) { return new MoneyView(amount.toPlainString(), "TND"); }
    private SummaryView summary(Snapshot s) {
        return new SummaryView(s.address(), s.items().stream().map(l -> new LineView(l.kind(), l.offerId(), l.label(),
                l.quantity(), money(l.unitPriceTnd()), money(l.lineTotalTnd()), l.offerVersion(), l.parentVersion(), l.components())).toList(),
                money(s.subtotalTnd()), money(s.deliveryTnd()), money(s.totalTnd()), s.quoteHash());
    }
    private OrderView view(Order order) { return new OrderView(order.id(), order.status(), "CASH_ON_DELIVERY",
            order.createdAt(), summary(order.snapshot())); }
    public record ItemInput(@NotNull Kind kind, @NotNull UUID offerId, @Min(1) @Max(999) long quantity,
                             @NotNull @Min(0) Long offerVersion, @NotNull @Min(0) Long parentVersion) { }
    public record PreviewInput(@NotNull @Size(min = 1, max = 100) List<@Valid ItemInput> items, @NotNull Address address) { }
    public record PlaceInput(@NotNull @Size(min = 1, max = 100) List<@Valid ItemInput> items, @NotNull Address address,
                             @NotNull @Pattern(regexp = "[0-9a-f]{64}") String quoteHash) { }
    public record MoneyView(String amount, String currency) { }
    public record LineView(Kind kind, UUID offerId, String label, long quantity, MoneyView unitPrice, MoneyView lineTotal,
                           long offerVersion, long parentVersion, List<SkuSnapshot> components) { }
    public record SummaryView(Address address, List<LineView> items, MoneyView subtotal, MoneyView delivery,
                              MoneyView total, String quoteHash) { }
    public record OrderView(UUID id, Status status, String paymentMethod, Instant createdAt, SummaryView summary) { }
}

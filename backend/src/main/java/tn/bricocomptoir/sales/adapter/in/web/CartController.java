package tn.bricocomptoir.sales.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.bricocomptoir.sales.adapter.transaction.CartTransactions;
import tn.bricocomptoir.sales.domain.CartModels.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartTransactions carts;
    public CartController(CartTransactions carts) { this.carts = carts; }

    @PostMapping("/estimate")
    public ResponseEntity<CartView> estimate(@Valid @RequestBody ItemsInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(view(null, carts.estimate(lines(input.items()))));
    }

    @GetMapping
    public ResponseEntity<CartView> get(@AuthenticationPrincipal(expression = "id") UUID customerId) {
        View result = carts.view(customerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(view(result.cart().version(), result.quote()));
    }

    @PutMapping
    public ResponseEntity<CartView> replace(@AuthenticationPrincipal(expression = "id") UUID customerId,
                                             @Valid @RequestBody ReplaceInput input) {
        View result = carts.replace(customerId, input.version(), lines(input.items()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(view(result.cart().version(), result.quote()));
    }

    @PostMapping("/merge")
    public ResponseEntity<CartView> merge(@AuthenticationPrincipal(expression = "id") UUID customerId,
                                           @Valid @RequestBody MergeInput input) {
        View result = carts.merge(customerId, input.mergeId(), lines(input.items()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(view(result.cart().version(), result.quote()));
    }

    private List<Line> lines(List<LineInput> input) {
        return input.stream().map(line -> new Line(line.kind(), line.offerId(), line.quantity())).toList();
    }
    private CartView view(Long version, Quote quote) {
        return new CartView(version, quote.items().stream().map(item -> new ItemView(
                item.line().kind(), item.line().offerId(), item.line().quantity(), item.label(),
                money(item.unitPriceTnd()), money(item.lineEstimateTnd()), item.offerVersion(),
                item.parentVersion())).toList(), money(quote.subtotalEstimateTnd()),
                quote.shortages());
    }
    private MoneyView money(java.math.BigDecimal amount) {
        return amount == null ? null : new MoneyView(amount.toPlainString(), "TND");
    }

    public record LineInput(@NotNull Kind kind, @NotNull UUID offerId,
                            @Min(1) @Max(999) long quantity) { }
    public record ItemsInput(@NotNull @Size(max = 100) List<@Valid LineInput> items) { }
    public record ReplaceInput(@NotNull @Min(0) Long version,
                               @NotNull @Size(max = 100) List<@Valid LineInput> items) { }
    public record MergeInput(@NotNull UUID mergeId,
                             @NotNull @Size(max = 100) List<@Valid LineInput> items) { }
    public record MoneyView(String amount, String currency) { }
    public record ItemView(Kind kind, UUID offerId, long quantity, String label,
                           MoneyView unitPrice, MoneyView lineEstimate,
                           Long offerVersion, Long parentVersion) { }
    public record CartView(Long version, List<ItemView> items, MoneyView subtotalEstimate,
                           List<Shortage> shortages) { }
}

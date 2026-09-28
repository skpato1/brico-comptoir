package tn.bricocomptoir.inventory.adapter.in.web;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.inventory.domain.InventoryModels.Stock;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
    private final InventoryTransactions inventory;
    public InventoryController(InventoryTransactions inventory) { this.inventory = inventory; }

    @GetMapping("/availability/{variantId}")
    public ResponseEntity<AvailabilityView> availability(@PathVariable UUID variantId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AvailabilityView(variantId, inventory.publicAvailability(variantId)));
    }

    @GetMapping("/admin/stock/{variantId}")
    public ResponseEntity<Stock> stock(@PathVariable UUID variantId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventory.stock(variantId));
    }

    @PostMapping("/admin/stock/adjustments")
    public ResponseEntity<Stock> adjust(@org.springframework.security.core.annotation.AuthenticationPrincipal(expression="id") UUID actor,
                                         @Valid @RequestBody AdjustmentInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventory.adjust(
                input.operationId(), input.variantId(), input.delta(), input.reason(), "C:"+actor));
    }

    public record AvailabilityView(UUID variantId, long available) { }
    public record AdjustmentInput(@NotNull UUID operationId, @NotNull UUID variantId,
                                  long delta, @NotBlank String reason) { }
}

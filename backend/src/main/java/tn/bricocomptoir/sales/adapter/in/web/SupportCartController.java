package tn.bricocomptoir.sales.adapter.in.web;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.bricocomptoir.sales.adapter.transaction.CartTransactions;
import tn.bricocomptoir.sales.domain.CartModels.Cart;

@RestController
@RequestMapping("/api/v1/admin/support/carts")
public class SupportCartController {
    private final CartTransactions carts;
    public SupportCartController(CartTransactions carts) { this.carts = carts; }
    @GetMapping("/{customerId}")
    public ResponseEntity<Cart> read(@PathVariable UUID customerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(carts.supportRead(customerId));
    }
}

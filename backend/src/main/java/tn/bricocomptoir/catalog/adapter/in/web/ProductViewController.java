package tn.bricocomptoir.catalog.adapter.in.web;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.bricocomptoir.catalog.adapter.transaction.ProductViewTransactions;
import tn.bricocomptoir.catalog.domain.ProductViewStats;

@RestController
@RequestMapping("/api/v1")
public class ProductViewController {
    private final ProductViewTransactions views;

    public ProductViewController(ProductViewTransactions views) { this.views = views; }

    @PostMapping("/products/{id}/views")
    public ResponseEntity<Void> record(@PathVariable UUID id, Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_CATALOG_MANAGER")
                        || authority.getAuthority().equals("ROLE_ORDER_MANAGER"))) views.record(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/admin/catalog/product-views")
    public ResponseEntity<ProductViewStats.Page> report(@RequestParam(defaultValue="7") int days,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(views.report(days, q, page, size));
    }
}

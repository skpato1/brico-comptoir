package tn.bricocomptoir.sales.adapter.in.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import tn.bricocomptoir.sales.domain.DeliverySettings;
import tn.bricocomptoir.sales.adapter.transaction.DeliverySettingsTransactions;
@RestController
@RequestMapping("/api/v1/admin/delivery")
public class DeliverySettingsController {
    private final DeliverySettingsTransactions settings;
    public DeliverySettingsController(DeliverySettingsTransactions settings) { this.settings=settings; }
    @GetMapping public ResponseEntity<DeliverySettings> get() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.get()); }
    @PutMapping public DeliverySettings save(@RequestBody DeliverySettings value,@org.springframework.security.core.annotation.AuthenticationPrincipal(expression="id") java.util.UUID actor) { return settings.save(value,"C:"+actor); }
}
